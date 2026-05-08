/**
 * Service Worker for AdaptEDU (simple offline cache)
 *
 * What it does:
 * Provide a basic offline cache for core UI assets so the app can
 * load when the user is offline. 
 *
 * 
 * 
 * Avoid caching dynamic API responses here — the UI calls `/api/*` which
 *   should be handled by the backend!!!!
 */
const CACHE_NAME = 'adaptedu-cache-v1';

// Add the core files of your web app here
const urlsToCache = [
    '/',
    '/script2.js',
    '/manifest.json',
    '/styles2.css'
];

// Install the service worker and cache files
self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => {
                return cache.addAll(urlsToCache);
            })
    );
    // Immediately take control of the page on next load so updated cache is used
    self.skipWaiting();
});
self.addEventListener('activate', event => {
    // On activation, remove any old caches not matching `CACHE_NAME`.
    event.waitUntil(
        caches.keys()
            .then(names => Promise.all(
                names.filter(name => name !== CACHE_NAME).map(name => caches.delete(name))
            ))
            .then(() => self.clients.claim())
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