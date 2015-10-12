$(document).ready(function() {
	// highlight image maps
    $.fn.maphilight.defaults.strokeColor = '000000';
	$('.map').maphighlight();

    // javascript hack to fix anchor links not working in WebView
	$('a[href*=#],area[href*=#]').click(function(e) {
		var hash = this.href.substr(this.href.indexOf('#'));
		var target = $('[name=\'' + hash.slice(1) +'\']');
		if (target.length) {
			$('html,body').animate({scrollTop: target.offset().top }, 500);
			return false;
		}
	});
});
