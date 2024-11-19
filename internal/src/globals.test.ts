import { test } from '@craftjs-typings/core';

test('global state', (t) => {
  t.eq(currentPlugin.name, 'craftjs-internal', 'currentPlugin is correct');
});
