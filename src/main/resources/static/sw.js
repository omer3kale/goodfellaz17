// ============================================
// GOODFELLAZ17 - Service Worker
// Cache-first strategy for offline PWA support
// ============================================

const CACHE_NAME = 'gf17-v1.0.0';
const STATIC_ASSETS = [
    '/',
    '/index.html',
    '/css/besiktas.css',
    '/js/app.js',
    '/manifest.json',
    '/assets/logo.svg',
    '/assets/icon-192.svg',
    '/assets/icon-512.svg'
];

// Install: Pre-cache static assets
self.addEventListener('install', event => {
    console.log('[SW] Installing...');
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => {
                console.log('[SW] Caching static assets');
                return cache.addAll(STATIC_ASSETS);
            })
            .then(() => self.skipWaiting())
    );
});

// Activate: Clean up old caches
self.addEventListener('activate', event => {
    console.log('[SW] Activating...');
    event.waitUntil(
        caches.keys()
            .then(cacheNames => {
                return Promise.all(
                    cacheNames
                        .filter(name => name !== CACHE_NAME)
                        .map(name => {
                            console.log('[SW] Deleting old cache:', name);
                            return caches.delete(name);
                        })
                );
            })
            .then(() => self.clients.claim())
    );
});

// Fetch: Cache-first for static, network-first for dynamic
self.addEventListener('fetch', event => {
    const { request } = event;
    const url = new URL(request.url);

    // Skip non-GET requests
    if (request.method !== 'GET') return;

    // Skip API requests (if any in future)
    if (url.pathname.startsWith('/api/')) return;

    // Cache-first strategy for static assets
    event.respondWith(
        caches.match(request)
            .then(cachedResponse => {
                if (cachedResponse) {
                    // Return cached version
                    return cachedResponse;
                }

                // Fetch from network
                return fetch(request)
                    .then(networkResponse => {
                        // Cache successful responses
                        if (networkResponse && networkResponse.status === 200) {
                            const responseClone = networkResponse.clone();
                            caches.open(CACHE_NAME)
                                .then(cache => cache.put(request, responseClone));
                        }
                        return networkResponse;
                    })
                    .catch(() => {
                        // Offline fallback for navigation requests
                        if (request.mode === 'navigate') {
                            return caches.match('/index.html');
                        }
                        return new Response('Offline', { status: 503 });
                    });
            })
    );
});

// Handle messages (for future use)
self.addEventListener('message', event => {
    if (event.data === 'skipWaiting') {
        self.skipWaiting();
    }
});
