$(document).ready(function() {
	// run debugger
    if (!document.getElementById('FirebugLite')){E = document['createElement' + 'NS'] && document.documentElement.namespaceURI;E = E ? document['createElement' + 'NS'](E, 'script') : document['createElement']('script');E['setAttribute']('id', 'FirebugLite');E['setAttribute']('src', 'https://getfirebug.com/' + 'firebug-lite.js' + '#startOpened');E['setAttribute']('FirebugLite', '4');(document['getElementsByTagName']('head')[0] || document['getElementsByTagName']('body')[0]).appendChild(E);E = new Image;E['setAttribute']('src', 'https://getfirebug.com/' + '#startOpened');}

    // continuously poll to see if we need to update any of the curated images
	var initRefresh = false;
    (function updateCuratedImages() {
    	try {
            if (window['report']) {
                if (!initRefresh) {
                    initRefresh = true;
                    $('img.stitched').each(function() {
                        if (this.dataset['path'] && this.dataset['timestamp']) {
                            var imagePath = this.dataset.path;
                            if (report.isEdited(imagePath) && !report.isUpToDate(imagePath, this.dataset.timestamp)) 
                            {
                                var base64 = report.getImageBase64(imagePath);
                                if (base64) {
                                    this.setAttribute('src',base64);
                                }
                            }
                        }
                    });
                }
                var changedImages = report.changedImages().split("\n");
                for (var i=0; i<changedImages.length; ++i) {
                    if (changedImages[i]) {
                        var id = changedImages[i].split('/').pop();
                        if (id) {
                            var base64 = report.getImageBase64(changedImages[i]);
                            if (base64) {
                                var element = document.getElementById(id);
                                if (element) {
                                    element.setAttribute('src',base64);
                                }
                            }
                        }
                    }
                }
            }
    	} 
    	catch (e) {
    		if (window['report']) report.jsLog(e.stack || e);
    	    (console.error || console.log).call(console, e.stack || e);
    	} 
        setTimeout(updateCuratedImages, 1000);
    })();
    
    try {
        // highlight image maps
        $.fn.maphilight.defaults.strokeColor = '000000';
        $('.map').maphilight();

        // javascript hack to fix anchor name links not working in WebView
        $('a[href*=#],area[href*=#]').click(function(e) {
            var hash = this.href.substr(this.href.indexOf('#'));
            var target = $('[name=\'' + hash.slice(1) +'\']');
            if (target.length) {
                $('html,body').animate({scrollTop: target.offset().top }, 500);
                return false;
            }
        });
        
        // another javascript hack to fix links not working in web view
        $('a:not(a[href*=#])').click(function(e) {
            if (this['href']) {
                if (window['report']) report.goToURL(this.href);
                window.location.href = this.href;
            }
            return true;
        });

        // show stage coordinates in tooltip
        $('img.stageCoords').powerTip({
            followMouse: true
        });
        $('img.stageCoords').bind('mousemove', function(e) {
            var parentOffset = $(this).offset(); 
            var width = $(this).attr('width');
            var height = $(this).attr('height');
            var minX = $(this).attr('data-min-x');
            var maxX = $(this).attr('data-max-x');
            var minY = $(this).attr('data-min-y');
            var maxY = $(this).attr('data-max-y');
            var relX = e.pageX - parentOffset.left;
            var relY = e.pageY - parentOffset.top;
            var stageX = minX < maxX? (relX / width * (maxX - minX)) + minX : (width - relX) / width * (minX - maxX) + maxX;
            var stageY = minY < maxY? (relY / height * (maxY - minY)) + minY : (height - relY) / height * (minY - maxY) + maxY;
            var title = $(this).attr('title');
            $('#powerTip').text((title? title+': ' : '')+'Stage Coords: ('+stageX+','+stageY+')');
        });
    } 
    catch (e) {
        if (window['report']) report.jsLog(e.stack || e);
        (console.error || console.log).call(console, e.stack || e);
    }
});