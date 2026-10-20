package com.msahil432.multitool.downloader

/**
 * JavaScript snippets injected into WebView pages to detect and capture downloadable media.
 *
 * Each snippet communicates results back to Kotlin via `window.MultiToolBridge`.
 */
object MediaDetectionJs {

    /**
     * Strategy 1: DOM Video & Animated Image Detection Script.
     *
     * Scans the document for:
     * - `<video>` tags and their child `<source>` elements
     * - `<img>` tags pointing to animated GIFs and WebP resources
     *
     * Dispatches deduplicated candidates back via `window.MultiToolBridge.onMediaDetected(json)`.
     */
    val DOM_SCAN_SCRIPT = """
        (function() {
            try {
                if (!window.MultiToolBridge || !window.MultiToolBridge.onMediaDetected) return;
                var results = [];
                
                // Scan <video> elements
                var videos = document.querySelectorAll('video');
                for (var i = 0; i < videos.length; i++) {
                    var video = videos[i];
                    var src = video.src || video.currentSrc;
                    var poster = video.poster || null;
                    var width = video.videoWidth || video.offsetWidth || -1;
                    var height = video.videoHeight || video.offsetHeight || -1;
                    var duration = (video.duration && !isNaN(video.duration)) ? video.duration : -1;
                    
                    if (src && src.length > 0 && !src.startsWith('blob:')) {
                        results.push({
                            url: src,
                            mimeType: video.type || null,
                            strategy: 'DOM_ELEMENT',
                            mediaType: 'VIDEO',
                            width: width,
                            height: height,
                            duration: duration,
                            thumbnail: poster
                        });
                    }
                    
                    // Scan <source> children
                    var sources = video.querySelectorAll('source');
                    for (var j = 0; j < sources.length; j++) {
                        var source = sources[j];
                        if (source.src && source.src.length > 0 && !source.src.startsWith('blob:')) {
                            results.push({
                                url: source.src,
                                mimeType: source.type || null,
                                strategy: 'DOM_ELEMENT',
                                mediaType: 'VIDEO',
                                width: width,
                                height: height,
                                duration: duration,
                                thumbnail: poster
                            });
                        }
                    }
                }
                
                // Scan <img> elements for GIF and WebP
                var imgs = document.querySelectorAll('img');
                for (var k = 0; k < imgs.length; k++) {
                    var img = imgs[k];
                    var imgSrc = img.src || img.currentSrc;
                    if (imgSrc) {
                        var lowerSrc = imgSrc.toLowerCase();
                        if (lowerSrc.indexOf('.gif') !== -1) {
                            results.push({
                                url: imgSrc,
                                mimeType: 'image/gif',
                                strategy: 'DOM_ELEMENT',
                                mediaType: 'GIF',
                                width: img.naturalWidth || -1,
                                height: img.naturalHeight || -1,
                                duration: -1,
                                thumbnail: null
                            });
                        } else if (lowerSrc.indexOf('.webp') !== -1) {
                            results.push({
                                url: imgSrc,
                                mimeType: 'image/webp',
                                strategy: 'DOM_ELEMENT',
                                mediaType: 'ANIMATED_WEBP',
                                width: img.naturalWidth || -1,
                                height: img.naturalHeight || -1,
                                duration: -1,
                                thumbnail: null
                            });
                        }
                    }
                }
                
                // Deduplicate by URL
                var seen = {};
                var unique = [];
                for (var m = 0; m < results.length; m++) {
                    if (!seen[results[m].url]) {
                        seen[results[m].url] = true;
                        unique.push(results[m]);
                    }
                }
                
                window.MultiToolBridge.onMediaDetected(JSON.stringify(unique));
            } catch (e) {
                // Ignore script errors on target webpage
            }
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
