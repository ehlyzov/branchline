import React from 'react';
import { createRoot } from 'react-dom/client';
import { BranchlinePlayground } from './playground';

const container = document.querySelector('.bl-playground');

if (container) {
  const root = createRoot(container);
  const defaultExampleId = container.getAttribute('data-default-example') ?? undefined;
  root.render(<BranchlinePlayground defaultExampleId={defaultExampleId || undefined} />);
}
