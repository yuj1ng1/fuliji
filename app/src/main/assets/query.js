function (config) {
    "use strict";
    function answer(state) { return JSON.stringify({state: state}); }
    if (config.source === "POWER" || config.source === "POWER_USAGE") {
        if (location.protocol !== "https:" || location.hostname !== "hqwx.fjut.edu.cn") return answer("unavailable");
        var usage = config.source === "POWER_USAGE";
        var panelId = usage ? "#tab4" : "#tab1";
        var panel = document.querySelector(panelId);
        var field = document.querySelector(usage ? "#picker-addr-view" : "#picker-addr");
        var address = document.querySelector(usage ? "#addr_view" : "#addr");
        if (!panel || !field) return answer("unavailable");
        if (getComputedStyle(panel).display === "none") {
            var tab = document.querySelector('a[href$="' + panelId + '"]');
            if (!tab) return answer("unavailable");
            tab.click();
        }
        if (!config.room) return answer("selection_required");
        var data = window.yd_roominfo;
        if (typeof data === "string") { try { data = JSON.parse(data); } catch (_) { return answer("unavailable"); } }
        var library = window.Zepto || window.jQuery;
        var picker = library && library(field).data("picker");
        if (!Array.isArray(data) || !address || !picker || !picker.params || typeof picker.params.onClose !== "function") return answer("unavailable");
        function normalize(value) { return String(value || "").trim().replace(/\s+/g, " "); }
        var requested = normalize(config.room);
        var matches = [];
        for (var a = 0; a < data.length; a++) {
            var buildings = data[a].subMenus || [];
            for (var b = 0; b < buildings.length; b++) {
                var rooms = buildings[b].subMenus || [];
                for (var r = 0; r < rooms.length; r++) {
                    var label = normalize(data[a].name + " " + buildings[b].name + " " + rooms[r].name);
                    if (label === requested) matches.push({label: label, ids: [String(data[a].id), String(buildings[b].id), String(rooms[r].id)]});
                }
            }
        }
        // Resolve against this authenticated page's own directory. Never guess IDs or choose a different room.
        if (matches.length !== 1) return answer("selection_required");
        for (var k = 0; k < 3; k++) if (!/^[a-zA-Z0-9_*.-]{1,100}$/.test(matches[0].ids[k])) return answer("unavailable");
        if (!usage && !document.querySelector("#addr_copy")) return answer("unavailable");
        field.value = matches[0].label;
        address.value = matches[0].ids.join(",");
        if (!usage) {
            document.querySelector("#addr_copy").value = "";
            var remaining = document.querySelector("#sydl");
            if (remaining) remaining.value = "";
        }
        // The school's verified picker-close callback performs the read-only balance/daily-usage query.
        // No recharge, payment, SMS or binding handler is called.
        picker.params.onClose(picker);
        return answer("query_submitted");
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
