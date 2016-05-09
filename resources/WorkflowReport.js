$(document).ready(function() {
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
		if (report) report.goToURL(this.href);
		window.location.href = this.href;
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
});
