#!/usr/bin/env python3
"""Isolated author sessions; only public diagnostics can reach a repair prompt."""
import argparse
import concurrent.futures
import fcntl
import hashlib
import itertools
import json
import os
from pathlib import Path
import random
import signal
import subprocess
import sys
import threading
import time

HERE = Path(__file__).resolve().parent
PUBLIC = Path('/tmp/branchline-authoring-public')
PRIVATE = Path('/tmp/branchline-authoring-private')
JOBS = Path('/tmp/branchline-authoring-jobs')
LANGUAGES = ['branchline', 'jsonata', 'kotlin']
MODEL = 'gpt-6.1-sol'
LIMIT_SECONDS = 1200
LIMIT_TOKENS = 12000
SEED = 20261017
LOCK = threading.Lock()


def dump(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run_process(args, *, cwd, timeout, stdin=None):
    start = time.monotonic()
    proc = subprocess.Popen(args, cwd=cwd, stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            text=True, start_new_session=True)
    timed_out = False
    try:
        out, err = proc.communicate(stdin, timeout=max(0.1, timeout))
    except subprocess.TimeoutExpired:
        timed_out = True
        os.killpg(proc.pid, signal.SIGKILL)
        out, err = proc.communicate()
    return proc.returncode, out, err, time.monotonic() - start, timed_out


def author(prompt, directory, seconds, prefix):
    configs = ['model_reasoning_effort="high"', 'features.shell_tool=false',
               'features.apps=false', 'features.multi_agent=false', 'features.hooks=false',
               'features.remote_plugin=false', 'features.memories=false',
               'skills.max_context_tokens=1',
               'web_search="disabled"', 'project_doc_max_bytes=0',
               f'model_instructions_file="{PUBLIC / "instructions.md"}"']
    args = ['sandbox-exec', '-f', str(PUBLIC / 'author.sb'), 'codex', 'exec',
            '--ignore-user-config', '--ephemeral', '--json', '--skip-git-repo-check',
            '-C', str(directory), '-s', 'read-only', '-m', MODEL,
            '--output-schema', str(PUBLIC / 'schema.json')]
    for config in configs:
        args += ['-c', config]
    args += ['-']
    code, out, err, elapsed, timeout = run_process(args, cwd=directory,
                                                 timeout=seconds, stdin=prompt)
    prefix.with_suffix('.jsonl').write_text(out)
    prefix.with_suffix('.stderr').write_text(err)
    events = []
    for line in out.splitlines():
        try:
            events.append(json.loads(line))
        except json.JSONDecodeError:
            pass
    messages = [e['item']['text'] for e in events if e.get('type') == 'item.completed'
                and e.get('item', {}).get('type') == 'agent_message']
    usage = next((e['usage'] for e in reversed(events) if e.get('type') == 'turn.completed'), None)
    tool_events = [e for e in events if e.get('type', '').startswith('item.')
                   and e.get('item', {}).get('type') not in ('agent_message', 'reasoning', 'error')]
    source = None
    parse_error = None
    if messages:
        try:
            source = json.loads(messages[-1])['source']
            if not isinstance(source, str):
                raise ValueError('source must be a string')
        except (ValueError, KeyError, TypeError) as exc:
            parse_error = str(exc)
    return dict(exit_code=code, elapsed_seconds=elapsed, timeout=timeout,
                usage=usage, source=source, parse_error=parse_error,
                tool_events=tool_events, completed=usage is not None)


def evaluate(language, source, cases, output, timeout=120):
    args = [sys.executable, str(HERE / 'evaluator' / 'evaluate.py'),
            '--language', language, '--source', str(source), '--cases', str(cases),
            '--output', str(output)]
    code, out, err, elapsed, timed_out = run_process(args, cwd=HERE, timeout=timeout)
    if timed_out or code != 0 or not output.exists():
        raise RuntimeError(f'evaluator infrastructure: exit={code} timeout={timed_out} {err[-2000:]} {out[-1000:]}')
    result = json.loads(output.read_text())
    result['evaluator_wall_seconds'] = elapsed
    return result


def passed(result):
    return bool(result and result.get('compile_ok') and result.get('cases')
                and all(case.get('ok') for case in result['cases']))


def prompt_for(task, language, reference, history, tokens_left):
    surfaces = {
        'branchline': 'Return a complete Branchline TRANSFORM Main program using input as its input value. OUTPUT must be the requested JSON object or array.',
        'jsonata': 'Return one complete JSONata expression evaluated with the input JSON as the root context. Runtime is Dashjoin JVM JSONata 0.9.10.',
        'kotlin': 'Return Kotlin 2.3.0 source defining fun transform(input: Any?): Any?. You may define helper functions in the same source. Default Kotlin imports plus java.math.* and kotlin.math.* are provided. Do not define main or a package. Input is ordinary Map/List/String/Boolean/Number/null; return the same JSON-compatible types. Use Number conversions, not assumptions about Int versus Long.',
    }
    parts = ['Implement the following task. All stated requirements, not only public examples, will be tested.',
             surfaces[language], 'Return only the schema object {"source":"..."}. No tools are allowed.',
             f'Total remaining sampled-token budget for this attempt: {tokens_left}.',
             'LANGUAGE REFERENCE\n' + reference,
             'TASK\n' + json.dumps(task, ensure_ascii=False)]
    if history:
        parts += ['Your previous submissions and PUBLIC evaluation feedback follow. Correct the solution. Hidden test feedback is unavailable.',
                  json.dumps(history, ensure_ascii=False)]
    return '\n\n'.join(parts)


def attempt(cell, task, hidden, reference):
    language, repeat = cell['language'], cell['repeat']
    name = f'{task["id"]}-{language}-r{repeat}'
    work = JOBS / name
    work.mkdir(parents=True, exist_ok=True)
    record_dir = PRIVATE / 'runs' / name
    record_dir.mkdir(parents=True, exist_ok=True)
    result_file = record_dir / 'result.json'
    if result_file.exists():
        return json.loads(result_file.read_text())
    if (record_dir/'started.json').exists():
        # Never silently grant a new budget after interruption. Raw partial work is retained.
        result = dict(**cell, category=task['category'], model=MODEL,
                      status='infrastructure_error', success=False,
                      error='Interrupted cell retained; no fresh author budget granted',
                      submissions=[])
        dump(result_file, result)
        return result
    started = time.time()
    clock = time.monotonic()
    result = dict(**cell, category=task['category'],
                  model=MODEL, reasoning='high', started_unix=started,
                  submissions=[], status='running', selected_submission=None)
    result['queue_seconds'] = started - cell['enqueued_unix']
    result['model_evidence'] = 'CLI requested gpt-6.1-sol; available in refreshed catalog; backend snapshot not returned by JSONL'
    result['seal_sha256'] = digest(HERE/'seal.json')
    result['transport_attempts'] = []
    dump(record_dir/'started.json', result)
    public_path = record_dir / 'public.json'
    dump(public_path, task['public'])
    history = []
    used = 0
    try:
        for index in range(4):
            remaining = LIMIT_SECONDS - (time.monotonic() - clock)
            if remaining <= 0 or used >= LIMIT_TOKENS:
                result['status'] = 'budget_exhausted'
                break
            prompt = prompt_for(task, language, reference, history, LIMIT_TOKENS - used)
            (record_dir / f'submission-{index}.prompt.txt').write_text(prompt)
            generated = None
            for transport_try in range(3):
                prefix = record_dir / f'submission-{index}-transport-{transport_try}'
                generated = author(prompt, work, remaining, prefix)
                result['transport_attempts'].append(dict(submission=index, retry=transport_try,
                    elapsed_seconds=generated['elapsed_seconds'], usage=generated['usage'],
                    tool_events=generated['tool_events'], completed=generated['completed'],
                    exit_code=generated['exit_code'], timeout=generated['timeout']))
                dump(record_dir/'checkpoint.json', result)
                if generated['tool_events']:
                    result['status'] = 'tool_protocol_violation'
                    break
                if generated['completed'] or generated['timeout']:
                    break
                if generated['source'] is not None:
                    # Unknown usage after an answer cannot be counted as a free retry.
                    result['unknown_sampled_usage'] = True
                    break
                remaining = LIMIT_SECONDS - (time.monotonic() - clock)
                result['unknown_transport_usage'] = True
            if generated['tool_events']:
                break
            if not generated['completed']:
                result['status'] = 'time_budget' if generated['timeout'] else 'infrastructure_error'
                result['generation_error'] = generated
                break
            usage = generated['usage']
            if not isinstance(usage.get('output_tokens'), int):
                raise RuntimeError('Author output token accounting unavailable')
            used += usage['output_tokens']
            submission = dict(index=index, usage=usage,
                              author_seconds=generated['elapsed_seconds'],
                              elapsed_seconds=time.monotonic()-clock,
                              tool_events=generated['tool_events'])
            result['submissions'].append(submission)
            dump(record_dir/'checkpoint.json', result)
            if generated['tool_events']:
                result['status'] = 'tool_protocol_violation'
                break
            if used > LIMIT_TOKENS or time.monotonic()-clock > LIMIT_SECONDS:
                result['status'] = 'budget_exhausted'
                break
            source = generated['source']
            source_path = record_dir / f'submission-{index}.source'
            if source is None:
                feedback = {'compile_ok':False, 'cases':[], 'error':generated['parse_error'] or 'Missing source field'}
            else:
                source_path.write_text(source)
                feedback = evaluate(language, source_path, public_path,
                                    record_dir/f'submission-{index}.public-result.json',
                                    min(120, LIMIT_SECONDS-(time.monotonic()-clock)))
            submission['public'] = feedback
            submission['elapsed_seconds'] = time.monotonic()-clock
            if time.monotonic()-clock > LIMIT_SECONDS:
                result['status'] = 'budget_exhausted'
                break
            result['selected_submission'] = index
            if passed(feedback):
                result['status'] = 'public_pass'
                break
            history.append({'source':source, 'feedback':feedback})
            result['status'] = 'public_fail'
            dump(record_dir/'checkpoint.json', result)
        result['authoring_seconds'] = time.monotonic()-clock
        result['sampled_tokens'] = used
        result['frozen_unix'] = time.time()
        # Freeze authoring BEFORE any hidden data is loaded into an evaluation process.
        dump(record_dir/'frozen.json', result)
        if result['status'] not in ('infrastructure_error','tool_protocol_violation','budget_exhausted','time_budget'):
            hidden_path = record_dir/'hidden.json'
            dump(hidden_path, hidden)
            # Evaluate first and final independently without resuming any author.
            for index in sorted({0, result['selected_submission']} - {None}):
                source_path = record_dir/f'submission-{index}.source'
                if source_path.exists():
                    outcome = evaluate(language, source_path, hidden_path,
                                       record_dir/f'submission-{index}.hidden-result.json')
                    result['submissions'][index]['hidden'] = outcome
        first = result['submissions'][0] if result['submissions'] else {}
        selected = result['selected_submission']
        final = result['submissions'][selected] if selected is not None else {}
        result['initial_success'] = passed(first.get('public')) and passed(first.get('hidden'))
        result['success'] = result['status'] == 'public_pass' and passed(final.get('hidden'))
        result['repairs'] = max(0, len(result['submissions'])-1)
        result['budget_verified'] = not (result.get('unknown_transport_usage') or result.get('unknown_sampled_usage'))
        result['finished_unix'] = time.time()
    except Exception as exc:
        result['status'] = 'infrastructure_error'
        result['error'] = repr(exc)
        result['success'] = False
    dump(result_file, result)
    with LOCK:
        with (PRIVATE/'progress.jsonl').open('a') as handle:
            handle.write(json.dumps({k:result.get(k) for k in ['task_id','language','repeat','status','success','repairs','authoring_seconds']})+'\n')
        print(name, result['status'], 'hidden_pass='+str(result.get('success')), flush=True)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--seal', action='store_true')
    parser.add_argument('--run', action='store_true')
    args = parser.parse_args()
    tasks = json.loads((PUBLIC/'tasks.json').read_text())
    assert len(tasks) == 30 and len({t['id'] for t in tasks}) == 30
    references = {lang:(HERE/'references'/f'{lang}.md').read_text() for lang in LANGUAGES}
    assert all(len(ref.encode()) <= 16000 for ref in references.values())
    hidden = json.loads((PRIVATE/'hidden.json').read_text())
    assert all(len(t['public']) >= 1 and len(hidden[t['id']]) >= 3 for t in tasks)
    if args.seal:
        schedule = []
        permutations = list(itertools.permutations(LANGUAGES))
        pairs = [(t['id'], r) for r in range(1,4) for t in tasks]
        random.Random(SEED).shuffle(pairs)
        for idx, (task_id, repeat) in enumerate(pairs):
            for language in permutations[idx % len(permutations)]:
                schedule.append(dict(task_id=task_id, language=language, repeat=repeat, order=len(schedule)))
        dump(PRIVATE/'schedule.json', schedule)
        paths = [PUBLIC/'tasks.json', PRIVATE/'hidden.json', PRIVATE/'fixtures_builder.py',
                 PUBLIC/'instructions.md', PUBLIC/'author.sb', PUBLIC/'schema.json',
                 HERE/'PROTOCOL.md', Path(__file__), PRIVATE/'schedule.json']
        paths += [PRIVATE/'author-preflight.json', PRIVATE/'probe-write-denial.jsonl']
        paths += sorted((HERE/'references').glob('*.md'))
        paths += sorted(p for p in (HERE/'evaluator').rglob('*') if p.is_file() and '__pycache__' not in str(p))
        runtime_config = PRIVATE/'runtime'/'config.json'
        runtime = json.loads(runtime_config.read_text())
        paths.append(runtime_config)
        paths += [Path(p) for p in set(runtime['classpath']+runtime['compiler_classpath'])]
        paths += sorted(p for p in (PRIVATE/'runtime'/'host').rglob('*') if p.is_file())
        paths += [PRIVATE/'runtime'/'host.sha256', PRIVATE/'runtime'/'selftest-report.json']
        manifest = dict(sealed_unix=time.time(), model=MODEL, seed=SEED, cells=270,
                        limit_seconds=LIMIT_SECONDS, sampled_token_limit=LIMIT_TOKENS,
                        engine_versions={k:runtime[k] for k in ['kotlin_version','jsonata_version','jackson_version']},
                        files={str(p):digest(p) for p in paths})
        dump(HERE/'seal.json', manifest)
        print('SEALED', digest(HERE/'seal.json'))
    if args.run:
        lock = (PRIVATE/'experiment.lock').open('w')
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        manifest=json.loads((HERE/'seal.json').read_text())
        assert all(digest(Path(p)) == h for p,h in manifest['files'].items()), 'Sealed material changed'
        mapping={t['id']:t for t in tasks}
        cells=json.loads((PRIVATE/'schedule.json').read_text())
        for cell in cells:
            cell['enqueued_unix'] = time.time()
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            futures=[pool.submit(attempt,c,mapping[c['task_id']],hidden[c['task_id']],references[c['language']]) for c in cells]
            results=[f.result() for f in futures]
        dump(PRIVATE/'all-results.json', results)
        print('COMPLETE',len(results),flush=True)


if __name__ == '__main__':
    main()
