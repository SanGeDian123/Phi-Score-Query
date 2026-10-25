const id = '[A-Za-z0-9_-]{1,160}';
const routes = [
  ['POST', /^save$/],
  ['POST', /^rks\/history$/],
  ['GET', /^songs\/(?:catalog|search|achievement-rates)$/],
  ['GET', /^leaderboard\/rks\/top$/], ['POST', /^leaderboard\/rks\/me$/],
  ['GET POST', /^checkin$/], ['GET', /^checkin\/leaderboard$/],
  ['POST', /^games\/rks-guess\/match$/], ['GET', /^games\/rks-guess\/leaderboard$/],
  ['GET', new RegExp('^games/rks-guess/' + id + '$')], ['POST', new RegExp('^games/rks-guess/' + id + '/(?:answer|next|leave)$')],
  ['GET', /^suggestions\/(?:random|mine|commented|notifications)$/],
  ['POST', /^suggestions\/posts$/], ['GET DELETE', new RegExp('^suggestions/posts/' + id + '$')],
  ['POST', new RegExp('^suggestions/posts/' + id + '/comments$')], ['DELETE', new RegExp('^suggestions/comments/' + id + '$')],
  ['POST', /^feedback$/], ['GET', /^feedback\/(?:mine|notifications|updates)$/],
  ['POST', new RegExp('^feedback/' + id + '/read$')], ['GET', new RegExp('^feedback/' + id + '/images/[0-9]+$')],
  ['POST', /^image\/(?:bn(?:\/user)?|bestn|p30|song)$/],
];
export function allowedRoute(path, method) {
  if (/[%\\\x00-\x1f]/.test(path) || path.includes('..')) return false;
  return routes.some(([methods, pattern]) => methods.split(' ').includes(method) && pattern.test(path));
}
export function allowedAsset(path, media = false) {
  if (/[%\\\x00-\x1f?#]/.test(path) || /(?:^|\/)\.{1,2}(?:\/|$)/.test(path)) return false;
  return (media ? /^\/suggestion-media\/[A-Za-z0-9_./-]+\.(?:png|jpg|jpeg|webp)$/i : /^\/(?:_ill\/(?:ill|illLow|illBlur)|avatar)\/[^/]+\.(?:png|webp|jpg)$/i).test(path);
}
