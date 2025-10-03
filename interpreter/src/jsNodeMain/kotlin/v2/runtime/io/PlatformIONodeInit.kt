@file:Suppress("UnsafeCastFromDynamic")

package v2.runtime.io

// Register Node.js implementations on globalThis so jsMain actuals can delegate without static require in browser bundles.
@Suppress("UNUSED_VARIABLE")
private val __initNodeIO = run {
    js(
        """
        (function() {
          try {
            var req = (typeof require !== 'undefined') ? require : null;
            if (req && typeof globalThis !== 'undefined') {
              globalThis.__blInitNodeIO = function() {
                var fs = req('fs');
                var p = req('path');
                globalThis.__blWriteText = function(path, text) {
                  var dir = p.dirname(path);
                  if (!fs.existsSync(dir)) { fs.mkdirSync(dir, { recursive: true }); }
                  fs.writeFileSync(path, text, 'utf8');
                };
                globalThis.__blReadText = function(path) {
                  return fs.readFileSync(path, 'utf8');
                };
              };
              if (!globalThis.__blWriteText) {
                globalThis.__blInitNodeIO();
              }
            }
          } catch (e) { /* ignore in non-node environments */ }
        })();
        """
    )
    0
}
