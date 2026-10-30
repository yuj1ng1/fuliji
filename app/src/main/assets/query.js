function (config) {
    "use strict";
    function answer(state) { return JSON.stringify({state: state}); }
    if (config.source === "POWER" || config.source === "POWER_USAGE") {
        if (location.hostname !== "hqwx.fjut.edu.cn") return answer("unavailable");
        if (config.source === "POWER" && !document.querySelector("#picker-addr")) return answer("unavailable");
        if (config.source === "POWER_USAGE") {
            var panel = document.querySelector("#tab4");
            if (!panel) return answer("unavailable");
            if (panel && getComputedStyle(panel).display === "none") {
                var tab = document.querySelector('a[href$="#tab4"]');
                if (tab) tab.click();
            }
        }
        return answer("selection_required");
    }
    if (location.hostname !== "jwxt-443.webvpn.fjut.edu.cn") return answer("unavailable");
    var yearSelect = document.querySelector("select#xnm, select[name=xnm]");
    var termSelect = document.querySelector("select#xqm, select[name=xqm]");
    var button = document.querySelector("#search_go");
    if (!yearSelect || !termSelect || !button) return answer("unavailable");
    function choose(select, desired, term) {
        if (!desired) return true;
        for (var i = 0; i < select.options.length; i++) {
            var label = select.options[i].text.trim();
            if ((term ? label.replace(/\D/g, "") : label.replace("—", "-")) === desired) {
                select.selectedIndex = i;
                select.dispatchEvent(new Event("change", {bubbles: true}));
                if (window.jQuery) window.jQuery(select).trigger("chosen:updated");
                return true;
            }
        }
        return false;
    }
    if (!choose(yearSelect, config.year, false) || !choose(termSelect, config.term, true)) return answer("selection_required");
    button.click();
    return answer("query_submitted");
}
