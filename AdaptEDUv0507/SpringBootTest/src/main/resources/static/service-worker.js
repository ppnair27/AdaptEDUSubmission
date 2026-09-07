const CACHE_NAME = 'adaptedu-cache-v2.2';

const urlsToCache = [
    '/',
    '/index2.html',
    '/styles2.css?v=2.2',
    '/script2.js?v=2.2',
    '/manifest.json'
];

// Install the service worker and cache core assets
self.addEventListener('install', event => {
    self.skipWaiting();
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => cache.addAll(urlsToCache).catch(err => console.warn('PWA precache warning:', err)))
    );
});

// Purge any stale caches from previous versions immediately
self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys().then(keys => {
            return Promise.all(
                keys.map(key => {
                    if (key !== CACHE_NAME) {
                        return caches.delete(key);
                    }
                })
            );
        }).then(() => self.clients.claim())
    );
});

// Network-first strategy: always fetch fresh from server, fall back to cache when offline
self.addEventListener('fetch', event => {
    // Only handle GET requests and skip backend API endpoints
    if (event.request.method !== 'GET' || event.request.url.includes('/api/')) {
        return;
    }

    event.respondWith(
        fetch(event.request)
            .then(response => {
                if (response && response.status === 200 && response.type === 'basic') {
                    const clone = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
                }
                return response;
            })
            .catch(() => caches.match(event.request))
    );
});