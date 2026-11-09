const V='dawa-v4',SHELL=['./','./index.html','./manifest.json','./icon-192.png','./icon-512.png'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(V).then(c=>Promise.all(SHELL.map(u=>c.add(u).catch(()=>{})))).then(()=>self.skipWaiting()))});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==V&&x.indexOf('dawa-')===0).map(x=>caches.delete(x)))).then(()=>self.clients.claim()))});
self.addEventListener('fetch',e=>{
 const r=e.request;if(r.method!=='GET')return;
 const u=new URL(r.url);
 if(r.mode==='navigate'){ /* app shell: network first, cached copy when offline */
  e.respondWith(fetch(r).then(x=>{const c=x.clone();caches.open(V).then(ch=>ch.put('./index.html',c));return x}).catch(()=>caches.match('./index.html').then(m=>m||caches.match('./'))));return}
 if(u.origin===location.origin){ /* same-origin files: cache first, refresh in background */
  e.respondWith(caches.match(r).then(m=>{const n=fetch(r).then(x=>{if(x&&x.ok){const c=x.clone();caches.open(V).then(ch=>ch.put(r,c))}return x}).catch(()=>m);return m||n}));return}
 if(/fonts\.(googleapis|gstatic)\.com/.test(u.host)){
  e.respondWith(caches.match(r).then(m=>m||fetch(r).then(x=>{if(x&&(x.ok||x.type==='opaque')){const c=x.clone();caches.open(V).then(ch=>ch.put(r,c))}return x})))}
});
self.addEventListener('notificationclick',e=>{e.notification.close();e.waitUntil(clients.matchAll({type:'window'}).then(l=>l[0]?l[0].focus():clients.openWindow('./index.html')))});
