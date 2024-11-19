import { test } from '@craftjs-typings/core';

test('fetch polyfill', async (t) => {
  const json = await (
    await fetch('https://api.github.com', {
      headers: new Headers([['User-Agent', 'CraftJS/test']]),
    })
  ).json();
  t.truthy(json.issues_url, 'basic JSON response seems correct');
});
