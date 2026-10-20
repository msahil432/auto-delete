package com.msahil432.multitool.downloader

/**
 * JavaScript snippets injected into WebView pages to detect and capture downloadable media.
 *
 * Each snippet communicates results back to Kotlin via `window.MultiToolBridge`.
 */
object MediaDetectionJs {

    /**
     * Runtime hooks and MutationObserver script.
     *
     * Intercepts `window.fetch`, `XMLHttpRequest`, HTML5 media playback events,
     * and observes DOM mutations (e.g. for dynamic SPA hydration on Reddit, RedGIFs).
     */
    val HOOKS_AND_OBSERVER_SCRIPT = """
        (function() {
            try {
                if (!window.MultiToolBridge || !window.MultiToolBridge.onMediaDetected) return;
                if (window.__multiToolHooksInstalled) return;
                window.__multiToolHooksInstalled = true;

                // Hook History and Navigation events to keep URL in sync for SPAs
                function reportUrlChange() {
                    try {
                        if (window.MultiToolBridge && window.MultiToolBridge.onUrlChanged) {
                            window.MultiToolBridge.onUrlChanged(window.location.href);
                        }
                    } catch (e) {}
                }

                try {
                    var origPushState = history.pushState;
                    if (origPushState) {
                        history.pushState = function() {
                            var ret = origPushState.apply(this, arguments);
                            reportUrlChange();
                            return ret;
                        };
                    }

                    var origReplaceState = history.replaceState;
                    if (origReplaceState) {
                        history.replaceState = function() {
                            var ret = origReplaceState.apply(this, arguments);
                            reportUrlChange();
                            return ret;
                        };
                    }

                    window.addEventListener('popstate', reportUrlChange);
                    window.addEventListener('hashchange', reportUrlChange);
                } catch (e) {}

                function reportOne(url, mime, strategy, poster) {
                    if (!url || typeof url !== 'string' || url.length < 5 || url.startsWith('blob:')) return;
                    var lower = url.toLowerCase();
                    if (lower.indexOf('/telemetry') !== -1 || lower.indexOf('/analytics') !== -1) return;
                    if (lower.indexOf('dash_audio') !== -1) return;

                    var mediaType = 'VIDEO';
                    if (lower.indexOf('.m3u8') !== -1 || lower.indexOf('hlsplaylist') !== -1) mediaType = 'HLS_STREAM';
                    else if (lower.indexOf('.mpd') !== -1 || lower.indexOf('dashplaylist') !== -1) mediaType = 'DASH_STREAM';
                    else if (lower.indexOf('.gif') !== -1) mediaType = 'GIF';
                    else if (lower.indexOf('.webp') !== -1) mediaType = 'ANIMATED_WEBP';

                    var item = {
                        url: url,
                        mimeType: mime || (mediaType === 'HLS_STREAM' ? 'application/vnd.apple.mpegurl' : 'video/mp4'),
                        strategy: strategy || 'RUNTIME_HOOK',
                        mediaType: mediaType,
                        thumbnail: poster || null
                    };
                    window.MultiToolBridge.onMediaDetected(JSON.stringify([item]));
                }

                // 1. Hook window.fetch for network-level media stream interception
                var origFetch = window.fetch;
                if (origFetch) {
                    window.fetch = function() {
                        var args = arguments;
                        var url = typeof args[0] === 'string' ? args[0] : (args[0] && args[0].url ? args[0].url : '');
                        if (url) {
                            var lUrl = url.toLowerCase();
                            var isMedia = lUrl.indexOf('.mp4') !== -1 ||
                                          lUrl.indexOf('.m3u8') !== -1 ||
                                          lUrl.indexOf('.mpd') !== -1 ||
                                          lUrl.indexOf('.webm') !== -1 ||
                                          lUrl.indexOf('.m4s') !== -1 ||
                                          lUrl.indexOf('v.redd.it') !== -1 ||
                                          lUrl.indexOf('media.redgifs.com') !== -1 ||
                                          lUrl.indexOf('thumbs2.redgifs.com') !== -1;
                            if (isMedia) {
                                reportOne(url, null, 'NETWORK_FETCH');
                            }
                        }
                        return origFetch.apply(this, args).then(function(res) {
                            try {
                                if (url && url.indexOf('api.redgifs.com') !== -1) {
                                    res.clone().json().then(function(data) {
                                        if (data && data.gif && data.gif.urls) {
                                            var u = data.gif.urls;
                                            var target = u.hd || u.sd || u.mobile;
                                            if (target) {
                                                reportOne(target, 'video/mp4', 'REDGIFS_API', data.gif.poster || null);
                                            }
                                        }
                                    }).catch(function() {});
                                }
                            } catch (e) {}
                            return res;
                        });
                    };
                }

                // 2. Hook XMLHttpRequest for media requests
                var origOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    if (url && typeof url === 'string') {
                        var lUrl = url.toLowerCase();
                        var isMedia = lUrl.indexOf('.mp4') !== -1 ||
                                      lUrl.indexOf('.m3u8') !== -1 ||
                                      lUrl.indexOf('.mpd') !== -1 ||
                                      lUrl.indexOf('.webm') !== -1 ||
                                      lUrl.indexOf('.m4s') !== -1 ||
                                      lUrl.indexOf('v.redd.it') !== -1 ||
                                      lUrl.indexOf('media.redgifs.com') !== -1 ||
                                      lUrl.indexOf('thumbs2.redgifs.com') !== -1;
                        if (isMedia) {
                            reportOne(url, null, 'NETWORK_XHR');
                        }
                    }
                    return origOpen.apply(this, arguments);
                };

                // 3. Capture media playback events on the document
                function onMediaEvent(e) {
                    var t = e.target;
                    if (t && (t.tagName === 'VIDEO' || t.tagName === 'AUDIO')) {
                        var src = t.currentSrc || t.src || t.getAttribute('src');
                        if (src && !src.startsWith('blob:')) {
                            reportOne(src, t.type || 'video/mp4', 'PLAYBACK_EVENT', t.poster || null);
                        }
                    }
                }
                window.addEventListener('play', onMediaEvent, true);
                window.addEventListener('loadeddata', onMediaEvent, true);
                window.addEventListener('loadedmetadata', onMediaEvent, true);

                // 4. MutationObserver for dynamically inserted DOM elements
                var debounceTimer = null;
                var observer = new MutationObserver(function() {
                    if (debounceTimer) return;
                    debounceTimer = setTimeout(function() {
                        debounceTimer = null;
                        if (window.__multiToolScanDom) {
                            window.__multiToolScanDom();
                        }
                    }, 400);
                });
                observer.observe(document.documentElement, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['src', 'stream-url', 'packaged-media-json', 'playback-media-json', 'data-src']
                });
            } catch (e) {}
        })();
    """.trimIndent()

    /**
     * Comprehensive DOM inspection script traversing standard elements, Shadow DOMs,
     * custom web components (Reddit `<shreddit-player>`), embedded RedGIFs metadata,
     * and dynamic media containers.
     */
    val DOM_SCAN_SCRIPT = """
        (function() {
            try {
                if (!window.MultiToolBridge || !window.MultiToolBridge.onMediaDetected) return;

                function isMediaUrl(url) {
                    if (!url || typeof url !== 'string' || url.length < 5) return false;
                    if (url.startsWith('blob:')) return false;
                    var lower = url.toLowerCase();
                    if (lower.indexOf('/telemetry') !== -1 || lower.indexOf('/analytics') !== -1) return false;
                    if (lower.indexOf('dash_audio') !== -1) return false;
                    var hasExt = lower.indexOf('.mp4') !== -1 ||
                                 lower.indexOf('.m3u8') !== -1 ||
                                 lower.indexOf('.mpd') !== -1 ||
                                 lower.indexOf('.webm') !== -1 ||
                                 lower.indexOf('.mkv') !== -1 ||
                                 lower.indexOf('.ts') !== -1 ||
                                 lower.indexOf('.m4s') !== -1 ||
                                 lower.indexOf('.m4v') !== -1 ||
                                 lower.indexOf('.mov') !== -1 ||
                                 lower.indexOf('.gif') !== -1 ||
                                 lower.indexOf('.webp') !== -1;
                    if (hasExt) return true;
                    if (lower.indexOf('v.redd.it') !== -1) return true;
                    if (lower.indexOf('media.redgifs.com') !== -1 || lower.indexOf('thumbs2.redgifs.com') !== -1) return true;
                    return false;
                }

                function classify(url) {
                    var lower = (url || '').toLowerCase();
                    if (lower.indexOf('.m3u8') !== -1 || lower.indexOf('hlsplaylist') !== -1) return 'HLS_STREAM';
                    if (lower.indexOf('.mpd') !== -1 || lower.indexOf('dashplaylist') !== -1) return 'DASH_STREAM';
                    if (lower.indexOf('.gif') !== -1) return 'GIF';
                    if (lower.indexOf('.webp') !== -1) return 'ANIMATED_WEBP';
                    return 'VIDEO';
                }

                function guessMime(u) {
                    var l = (u || '').toLowerCase();
                    if (l.indexOf('.m3u8') !== -1) return 'application/vnd.apple.mpegurl';
                    if (l.indexOf('.mpd') !== -1) return 'application/dash+xml';
                    if (l.indexOf('.webm') !== -1) return 'video/webm';
                    if (l.indexOf('.gif') !== -1) return 'image/gif';
                    if (l.indexOf('.webp') !== -1) return 'image/webp';
                    return 'video/mp4';
                }

                function getAllNodes(root) {
                    var nodes = [];
                    function traverse(node) {
                        if (!node) return;
                        nodes.push(node);
                        if (node.shadowRoot) {
                            traverse(node.shadowRoot);
                        }
                        var children = node.children || [];
                        for (var i = 0; i < children.length; i++) {
                            traverse(children[i]);
                        }
                    }
                    traverse(root);
                    return nodes;
                }

                window.__multiToolScanDom = function() {
                    try {
                        var results = [];

                        // 1. Scan standard <video> and nested <source> elements
                        var videos = document.querySelectorAll('video');
                        for (var vi = 0; vi < videos.length; vi++) {
                            var v = videos[vi];
                            var vSrc = v.src || v.currentSrc || v.getAttribute('src');
                            var vPoster = v.poster || null;
                            var vw = v.videoWidth || v.offsetWidth || -1;
                            var vh = v.videoHeight || v.offsetHeight || -1;
                            var vdur = (v.duration && !isNaN(v.duration)) ? v.duration : -1;
                            if (vSrc && isMediaUrl(vSrc)) {
                                results.push({
                                    url: vSrc,
                                    mimeType: v.type || 'video/mp4',
                                    strategy: 'DOM_ELEMENT',
                                    mediaType: classify(vSrc),
                                    width: vw,
                                    height: vh,
                                    duration: vdur,
                                    thumbnail: vPoster
                                });
                            }
                            var vSources = v.querySelectorAll('source');
                            for (var vsi = 0; vsi < vSources.length; vsi++) {
                                var vsSrc = vSources[vsi].src || vSources[vsi].getAttribute('src');
                                if (vsSrc && isMediaUrl(vsSrc)) {
                                    results.push({
                                        url: vsSrc,
                                        mimeType: vSources[vsi].type || 'video/mp4',
                                        strategy: 'DOM_ELEMENT',
                                        mediaType: classify(vsSrc),
                                        width: vw,
                                        height: vh,
                                        duration: vdur,
                                        thumbnail: vPoster
                                    });
                                }
                            }
                        }

                        // Also query document-level <source> tags
                        var docSources = document.querySelectorAll('source');
                        for (var dsi = 0; dsi < docSources.length; dsi++) {
                            var dSrc = docSources[dsi].src || docSources[dsi].getAttribute('src');
                            if (dSrc && isMediaUrl(dSrc)) {
                                results.push({
                                    url: dSrc,
                                    mimeType: docSources[dsi].type || 'video/mp4',
                                    strategy: 'DOM_ELEMENT',
                                    mediaType: classify(dSrc)
                                });
                            }
                        }

                        // 2. Scan standard <img> elements for GIF and WebP
                        var imgs = document.querySelectorAll('img');
                        for (var imi = 0; imi < imgs.length; imi++) {
                            var im = imgs[imi];
                            var iSrc = im.src || im.currentSrc || im.getAttribute('src');
                            if (iSrc && isMediaUrl(iSrc)) {
                                var liSrc = iSrc.toLowerCase();
                                if (liSrc.indexOf('.gif') !== -1) {
                                    results.push({
                                        url: iSrc,
                                        mimeType: 'image/gif',
                                        strategy: 'DOM_ELEMENT',
                                        mediaType: 'GIF',
                                        width: im.naturalWidth || -1,
                                        height: im.naturalHeight || -1
                                    });
                                } else if (liSrc.indexOf('.webp') !== -1) {
                                    results.push({
                                        url: iSrc,
                                        mimeType: 'image/webp',
                                        strategy: 'DOM_ELEMENT',
                                        mediaType: 'ANIMATED_WEBP',
                                        width: im.naturalWidth || -1,
                                        height: im.naturalHeight || -1
                                    });
                                }
                            }
                        }

                        // 3. Scan Shadow DOM and custom SPA components (e.g. Reddit <shreddit-player>)
                        var allElements = getAllNodes(document.body || document.documentElement);

                        for (var i = 0; i < allElements.length; i++) {
                            var el = allElements[i];
                            var tag = (el.tagName || '').toLowerCase();

                            // 1. Reddit <shreddit-player> custom component
                            if (tag === 'shreddit-player') {
                                var sUrl = el.getAttribute('stream-url') || el.getAttribute('src');
                                var poster = el.getAttribute('preview-url') || el.getAttribute('poster');
                                if (sUrl && isMediaUrl(sUrl)) {
                                    results.push({
                                        url: sUrl,
                                        mimeType: sUrl.indexOf('.m3u8') !== -1 ? 'application/vnd.apple.mpegurl' : 'video/mp4',
                                        strategy: 'REDDIT_PLAYER',
                                        mediaType: classify(sUrl),
                                        thumbnail: poster
                                    });
                                }
                                var pkgMedia = el.getAttribute('packaged-media-json') || el.getAttribute('playback-media-json');
                                if (pkgMedia) {
                                    try {
                                        var parsed = JSON.parse(pkgMedia);
                                        var hls = parsed.playbackHlsUrl;
                                        var mp4 = parsed.playbackMp4Url || parsed.fallbackUrl;
                                        var dash = parsed.playbackDashUrl;
                                        if (hls && isMediaUrl(hls)) {
                                            results.push({
                                                url: hls,
                                                mimeType: 'application/vnd.apple.mpegurl',
                                                strategy: 'REDDIT_PLAYER',
                                                mediaType: 'HLS_STREAM',
                                                thumbnail: poster
                                            });
                                        }
                                        if (mp4 && isMediaUrl(mp4)) {
                                            results.push({
                                                url: mp4,
                                                mimeType: 'video/mp4',
                                                strategy: 'REDDIT_PLAYER',
                                                mediaType: 'VIDEO',
                                                thumbnail: poster
                                            });
                                        }
                                        if (dash && isMediaUrl(dash)) {
                                            results.push({
                                                url: dash,
                                                mimeType: 'application/dash+xml',
                                                strategy: 'REDDIT_PLAYER',
                                                mediaType: 'DASH_STREAM',
                                                thumbnail: poster
                                            });
                                        }
                                    } catch (pe) {}
                                }
                            }

                            // 2. Standard <video> elements (including those inside shadow roots)
                            if (tag === 'video') {
                                var src = el.src || el.currentSrc || el.getAttribute('src');
                                var vPoster = el.poster || null;
                                var w = el.videoWidth || el.offsetWidth || -1;
                                var h = el.videoHeight || el.offsetHeight || -1;
                                var dur = (el.duration && !isNaN(el.duration)) ? el.duration : -1;

                                if (src && isMediaUrl(src)) {
                                    results.push({
                                        url: src,
                                        mimeType: el.type || 'video/mp4',
                                        strategy: 'DOM_ELEMENT',
                                        mediaType: classify(src),
                                        width: w,
                                        height: h,
                                        duration: dur,
                                        thumbnail: vPoster
                                    });
                                }

                                var sources = el.querySelectorAll('source');
                                for (var s = 0; s < sources.length; s++) {
                                    var sSrc = sources[s].src || sources[s].getAttribute('src');
                                    if (sSrc && isMediaUrl(sSrc)) {
                                        results.push({
                                            url: sSrc,
                                            mimeType: sources[s].type || 'video/mp4',
                                            strategy: 'DOM_ELEMENT',
                                            mediaType: classify(sSrc),
                                            width: w,
                                            height: h,
                                            duration: dur,
                                            thumbnail: vPoster
                                        });
                                    }
                                }
                            }

                            // 3. Custom video data attributes
                            var dataSrc = el.getAttribute('data-src') ||
                                          el.getAttribute('data-video-src') ||
                                          el.getAttribute('data-url') ||
                                          el.getAttribute('data-stream-url') ||
                                          el.getAttribute('data-mp4') ||
                                          el.getAttribute('data-hls');
                            if (dataSrc && isMediaUrl(dataSrc)) {
                                results.push({
                                    url: dataSrc,
                                    mimeType: guessMime(dataSrc),
                                    strategy: 'DOM_ELEMENT',
                                    mediaType: classify(dataSrc)
                                });
                            }

                            // 4. RedGIFs <iframe> embeds
                            if (tag === 'iframe') {
                                var fSrc = el.src || el.getAttribute('src') || '';
                                if (fSrc.indexOf('redgifs.com/ifr/') !== -1) {
                                    var gId = fSrc.substring(fSrc.indexOf('redgifs.com/ifr/') + 16).split('?')[0].split('#')[0];
                                    if (gId) {
                                        results.push({
                                            url: 'https://media.redgifs.com/' + gId + '.mp4',
                                            mimeType: 'video/mp4',
                                            strategy: 'REDGIFS_EMBED',
                                            mediaType: 'VIDEO'
                                        });
                                    }
                                }
                            }

                            // 5. <img> elements for GIF and WebP
                            if (tag === 'img') {
                                var imgSrc = el.src || el.currentSrc || el.getAttribute('src');
                                if (imgSrc && isMediaUrl(imgSrc)) {
                                    var lSrc = imgSrc.toLowerCase();
                                    if (lSrc.indexOf('.gif') !== -1) {
                                        results.push({
                                            url: imgSrc,
                                            mimeType: 'image/gif',
                                            strategy: 'DOM_ELEMENT',
                                            mediaType: 'GIF',
                                            width: el.naturalWidth || -1,
                                            height: el.naturalHeight || -1
                                        });
                                    } else if (lSrc.indexOf('.webp') !== -1) {
                                        results.push({
                                            url: imgSrc,
                                            mimeType: 'image/webp',
                                            strategy: 'DOM_ELEMENT',
                                            mediaType: 'ANIMATED_WEBP',
                                            width: el.naturalWidth || -1,
                                            height: el.naturalHeight || -1
                                        });
                                    }
                                }
                            }
                        }

                        // 6. Inspect metadata scripts (RedGIFs Next.js and JSON-LD)
                        var scripts = document.querySelectorAll('script[id="__NEXT_DATA__"], script[type="application/ld+json"]');
                        for (var sc = 0; sc < scripts.length; sc++) {
                            var scText = scripts[sc].textContent || '';
                            var rgMatches = scText.match(/https:\/\/(media|thumbs2)\.redgifs\.com\/[a-zA-Z0-9_\-]+\.mp4/gi);
                            if (rgMatches) {
                                for (var rgm = 0; rgm < rgMatches.length; rgm++) {
                                    results.push({
                                        url: rgMatches[rgm],
                                        mimeType: 'video/mp4',
                                        strategy: 'PAGE_METADATA',
                                        mediaType: 'VIDEO'
                                    });
                                }
                            }
                            var rdMatches = scText.match(/https:\/\/v\.redd\.it\/[a-zA-Z0-9_\-]+\/(HLSPlaylist\.m3u8|DASHPlaylist\.mpd|DASH_[0-9]+\.mp4)/gi);
                            if (rdMatches) {
                                for (var rdm = 0; rdm < rdMatches.length; rdm++) {
                                    results.push({
                                        url: rdMatches[rdm],
                                        mimeType: rdMatches[rdm].indexOf('.m3u8') !== -1 ? 'application/vnd.apple.mpegurl' : 'video/mp4',
                                        strategy: 'PAGE_METADATA',
                                        mediaType: classify(rdMatches[rdm])
                                    });
                                }
                            }
                        }

                        // Deduplicate
                        var seen = {};
                        var unique = [];
                        for (var m = 0; m < results.length; m++) {
                            if (!seen[results[m].url]) {
                                seen[results[m].url] = true;
                                unique.push(results[m]);
                            }
                        }

                        if (unique.length > 0) {
                            window.MultiToolBridge.onMediaDetected(JSON.stringify(unique));
                        }
                    } catch (err) {}
                };

                window.__multiToolScanDom();
            } catch (e) {}
        })();
    """.trimIndent()

    /**
     * Strategy 3: Canvas & Non-Standard Video Container Detection Script.
     *
     * Identifies canvas elements that may be rendering video frames, as well as
     * elements with video CSS background images or custom video data attributes.
     */
    val CANVAS_DETECT_SCRIPT = """
        (function() {
            try {
                if (!window.MultiToolBridge || !window.MultiToolBridge.onCanvasCandidates) return;
                var candidates = [];
                
                // 1. Find <canvas> elements with dimensions suggesting video
                var canvases = document.querySelectorAll('canvas');
                for (var i = 0; i < canvases.length; i++) {
                    var c = canvases[i];
                    if (c.width >= 160 && c.height >= 90) {
                        candidates.push({
                            type: 'canvas',
                            elementIndex: i,
                            width: c.width,
                            height: c.height,
                            selector: getUniqueSelector(c)
                        });
                    }
                }
                
                // 2. Find elements with video-like background or data attributes
                var allElements = document.querySelectorAll('*');
                for (var j = 0; j < allElements.length; j++) {
                    var el = allElements[j];
                    var bg = window.getComputedStyle ? window.getComputedStyle(el).backgroundImage : '';
                    if (bg && bg.match(/\.(mp4|webm|mkv)/i)) {
                        var urlMatch = bg.match(/url\(['"]?([^'"]+)['"]?\)/);
                        if (urlMatch && urlMatch[1]) {
                            candidates.push({
                                type: 'css_bg_video',
                                selector: getUniqueSelector(el),
                                width: el.offsetWidth || -1,
                                height: el.offsetHeight || -1,
                                url: urlMatch[1]
                            });
                        }
                    }
                    
                    var dataVideoSrc = el.getAttribute('data-video-src')
                        || el.getAttribute('data-src')
                        || el.getAttribute('data-video-url');
                    if (dataVideoSrc && dataVideoSrc.match(/\.(mp4|webm|mkv|m3u8)/i)) {
                        candidates.push({
                            type: 'data_attr_video',
                            selector: getUniqueSelector(el),
                            width: el.offsetWidth || -1,
                            height: el.offsetHeight || -1,
                            url: dataVideoSrc
                        });
                    }
                }
                
                function getUniqueSelector(el) {
                    if (el.id) return '#' + el.id;
                    var path = [];
                    var current = el;
                    while (current && current.nodeType === 1) {
                        var tag = current.tagName.toLowerCase();
                        if (current.id) {
                            path.unshift('#' + current.id);
                            break;
                        }
                        var sibling = current;
                        var nth = 1;
                        while (sibling = sibling.previousElementSibling) {
                            if (sibling.tagName === current.tagName) nth++;
                        }
                        path.unshift(tag + ':nth-of-type(' + nth + ')');
                        current = current.parentElement;
                    }
                    return path.join(' > ');
                }
                
                window.MultiToolBridge.onCanvasCandidates(JSON.stringify(candidates));
            } catch (e) {
                // Ignore script errors on target webpage
            }
        })();
    """.trimIndent()

    /**
     * Builds JavaScript that initiates canvas stream recording via `MediaRecorder`.
     *
     * @param selector CSS selector for target `<canvas>` element.
     * @param durationMs Maximum capture duration in milliseconds.
     * @return Runnable JavaScript string.
     */
    fun canvasCaptureScript(selector: String, durationMs: Long): String {
        val sanitizedSelector = selector.replace("'", "\\'")
        return """
            (function() {
                try {
                    var canvas = document.querySelector('$sanitizedSelector');
                    if (!canvas || canvas.tagName.toLowerCase() !== 'canvas') {
                        if (window.MultiToolBridge && window.MultiToolBridge.onCaptureError) {
                            window.MultiToolBridge.onCaptureError('Target element not found or not a canvas');
                        }
                        return;
                    }
                    
                    if (!canvas.captureStream) {
                        if (window.MultiToolBridge && window.MultiToolBridge.onCaptureError) {
                            window.MultiToolBridge.onCaptureError('captureStream not supported by this WebView');
                        }
                        return;
                    }
                    
                    var stream = canvas.captureStream(30);
                    var recorder;
                    try {
                        recorder = new MediaRecorder(stream, { mimeType: 'video/webm;codecs=vp9' });
                    } catch (e1) {
                        try {
                            recorder = new MediaRecorder(stream, { mimeType: 'video/webm' });
                        } catch (e2) {
                            recorder = new MediaRecorder(stream);
                        }
                    }
                    
                    var chunks = [];
                    recorder.ondataavailable = function(e) {
                        if (e.data && e.data.size > 0) chunks.push(e.data);
                    };
                    
                    recorder.onstop = function() {
                        var blob = new Blob(chunks, { type: 'video/webm' });
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            if (window.MultiToolBridge && window.MultiToolBridge.onCanvasCaptured) {
                                window.MultiToolBridge.onCanvasCaptured(reader.result, 'video/webm');
                            }
                        };
                        reader.readAsDataURL(blob);
                    };
                    
                    recorder.onerror = function(e) {
                        if (window.MultiToolBridge && window.MultiToolBridge.onCaptureError) {
                            window.MultiToolBridge.onCaptureError('Recording error: ' + (e.error ? e.error.name : 'unknown'));
                        }
                    };
                    
                    recorder.start();
                    if (window.MultiToolBridge && window.MultiToolBridge.onCaptureStarted) {
                        window.MultiToolBridge.onCaptureStarted('$sanitizedSelector', $durationMs);
                    }
                    
                    setTimeout(function() {
                        if (recorder.state === 'recording') {
                            recorder.stop();
                        }
                    }, $durationMs);
                } catch (err) {
                    if (window.MultiToolBridge && window.MultiToolBridge.onCaptureError) {
                        window.MultiToolBridge.onCaptureError('captureStream failed: ' + err.message);
                    }
                }
            })();
        """.trimIndent()
    }
}
