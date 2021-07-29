$(document).ready(function() {
	;(async function() {
        window.report = new Proxy({}, {
            get: function(target, property, receiver) {
                return async function(...args) {
                    return fetch(location.host+"/report/"+
						encodeURIComponent(property)+"?args="+encodeURIComponent(JSON.stringify(args))).json();
                };
            }
        });

        // continuously poll to see if we need to update any of the curated images
        var initRefresh = false;
        (function updateCuratedImages() {
            try {
                if (!initRefresh) {
                    initRefresh = true;
                    $('img.stitched').each(function() {
                        if (this.dataset['path'] && this.dataset['timestamp']) {
                            var imagePath = this.dataset.path;
                            if (await report.isEdited(imagePath) && !await report.isUpToDate(imagePath, this.dataset.timestamp)) 
                            {
                                var base64 = await report.getImageBase64(imagePath);
                                if (base64) {
                                    this.setAttribute('src',base64);
                                }
                            }
                        }
                    });
                }
                var changedImages = await report.changedImages().split("\n");
                for (var i=0; i<changedImages.length; ++i) {
                    if (changedImages[i]) {
                        var id = changedImages[i].split('/').pop();
                        if (id) {
                            var base64 = await report.getImageBase64(changedImages[i]);
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
            catch (e) {
                await report.jsLog(e.stack || e);
                if (window['console']) (console.error || console.log).call(console, e.stack || e);
            } 
            setTimeout(updateCuratedImages, 1000);
        })();
        
        try {
            // highlight image maps
            $.fn.maphilight.defaults.strokeColor = '000000';
            $('.map').maphilight();

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
            await report.jsLog(e.stack || e);
            if (window['console']) (console.error || console.log).call(console, e.stack || e);
        }
	})();
});