const CACHE_NAME = 'adaptedu-cache-v1';

// Add the core files of your web app here
const urlsToCache = [
    '/',
    '/script2.js',
    '/manifest.json'
    // Add your CSS file here, e.g., '/style.css'
];

// Install the service worker and cache files
self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => {
                return cache.addAll(urlsToCache);
            })
    );
});

// Serve cached files when offline
self.addEventListener('fetch', event => {
    event.respondWith(
        caches.match(event.request)
            .then(response => {
                // Return cached version or fetch from the network
                return response || fetch(event.request);
            })
    );
});