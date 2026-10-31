(function () {
    "use strict";
    var root = document.documentElement;
    var marker = "fuliji-power-viewport";
    var styleId = "fuliji-power-viewport-style";
    var query = new URL(location.href).searchParams;
    var allowed = location.protocol === "https:" && location.hostname === "hqwx.fjut.edu.cn" &&
        (!location.port || location.port === "443") && location.pathname === "/agent/app/index.php" &&
        query.getAll("i").length === 1 && query.getAll("c").length === 1 && query.getAll("m").length === 1 && query.getAll("do").length === 1 &&
        query.get("i") === "67" && query.get("c") === "entry" &&
        ((query.get("m") === "maihu_portal" && query.get("do") === "register") ||
         (query.get("m") === "maihu_yktservice" && query.get("do") === "rechargeelec"));
    if (!allowed) {
        root.classList.remove(marker);
        var old = document.getElementById(styleId);
        if (old) old.remove();
        return JSON.stringify({state: "outside"});
    }
    var page = document.querySelector(".page.page-current");
    var content = page && page.querySelector(".content");
    if (!document.body || !page || !content || getComputedStyle(page).display === "none" || innerHeight <= 0) return JSON.stringify({state: "waiting"});
    var wasCollapsed = document.body.getBoundingClientRect().height < 1 || page.getBoundingClientRect().height < 1 || content.getBoundingClientRect().height < 1;
    if (wasCollapsed || root.classList.contains(marker)) {
        var style = document.getElementById(styleId);
        if (!style) {
            style = document.createElement("style");
            style.id = styleId;
            (document.head || root).appendChild(style);
        }
        // Some WebViews keep percentage/vh layout heights at zero after the native header resizes.
        // Use the measured CSS pixel height and update it when the keyboard or window size changes.
        var pixels = Math.ceil(innerHeight) + "px";
        var css = "html." + marker + ",html." + marker + " body,html." + marker + " body>.page-group,html." + marker + " .page.page-current" +
            "{height:" + pixels + "!important;min-height:" + pixels + "!important;}";
        if (style.textContent !== css) style.textContent = css;
        root.classList.add(marker);
    }
    var bodyHeight = document.body.getBoundingClientRect().height;
    var pageHeight = page.getBoundingClientRect().height;
    var contentHeight = content.getBoundingClientRect().height;
    return JSON.stringify({state: bodyHeight > 0 && pageHeight > 0 && contentHeight > 0 ? "visible" : "collapsed",
        adapted: root.classList.contains(marker), viewportHeight: Math.round(innerHeight),
        bodyHeight: Math.round(bodyHeight), pageHeight: Math.round(pageHeight), contentHeight: Math.round(contentHeight)});
})();
