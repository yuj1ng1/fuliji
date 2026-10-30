(function () {
    "use strict";
    var source = "__CAMPUS_SOURCE__";
    var visible = function (node) { return node && node.getBoundingClientRect().width > 0; };
    if (Array.from(document.querySelectorAll("input[type=password]")).some(visible)) {
        return JSON.stringify({status: "login"});
    }
    var text = (document.body && document.body.innerText) || "";
    if (/正在请求数据|查询中(?:\.{3}|…)/.test(text)) return JSON.stringify({status: "loading"});
    if (source === "POWER") {
        var room = document.querySelector("#picker-addr");
        var energy = document.querySelector("#sydl");
        if (!visible(room) || !visible(energy) || !room.value || !energy.value) return JSON.stringify({status: "not_queried"});
        return JSON.stringify({status: "ok", scope: room.value.trim(), remaining: energy.value.trim()});
    }
    if (source === "POWER_USAGE") {
        var panel = document.querySelector("#tab4");
        var usageRoom = document.querySelector("#picker-addr-view");
        var usageTable = panel && panel.querySelector("ul.table");
        if (!visible(panel) || !usageRoom || !usageRoom.value || !usageTable) return JSON.stringify({status: "not_queried"});
        var usageRows = Array.from(usageTable.querySelectorAll("li")).map(function (row) {
            return row.innerText.trim();
        }).filter(function (row) { return row && !/^日期\s+用量$/.test(row); });
        if (!usageRows.length) return JSON.stringify({status: "not_queried"});
        return JSON.stringify({status: "ok", scope: usageRoom.value.trim(), usage: usageRows});
    }
    if (source === "COURSES") {
        var table = document.querySelector("#kbgrid_table_0");
        if (!table) return JSON.stringify({status: "unknown"});
        var scopeMatch = table.innerText.match(/\d{4}-\d{4}学年第\d学期/);
        if (!scopeMatch) return JSON.stringify({status: "unknown"});
        var cells = Array.from(table.querySelectorAll("td[id]")).filter(function (cell) {
            return /^[1-7]-\d+$/.test(cell.id) && cell.innerText.trim();
        }).map(function (cell) {
            return {day: Number(cell.id.split("-")[0]), text: cell.innerText.trim()};
        });
        var unfixed = Array.from(table.querySelectorAll("td")).filter(function (cell) {
            return !cell.id && /实践课程[：:]|其它课程[：:]|其他课程[：:]/.test(cell.innerText);
        }).map(function (cell) { return cell.innerText.trim(); });
        return JSON.stringify({status: "ok", scope: scopeMatch[0], cells: cells, unfixed: unfixed});
    }
    if (text.indexOf("请选择筛选条件") >= 0) return JSON.stringify({status: "not_queried"});
    var yearSelect = document.querySelector("select#xnm, select[name=xnm]");
    var termSelect = document.querySelector("select#xqm, select[name=xqm]");
    var selectedText = function (select) {
        return select && select.selectedOptions.length === 1 ? select.selectedOptions[0].text.trim() : "";
    };
    var year = selectedText(yearSelect);
    var term = selectedText(termSelect);
    if (!/^\d{4}[-—]\d{4}$/.test(year) || !/^(?:第)?[123](?:学期)?$/.test(term)) {
        return JSON.stringify({status: "unknown"});
    }
    var scope = year + " · 第" + term.replace(/\D/g, "") + "学期";
    var grid = document.querySelector("#tabGrid");
    if (!grid) return JSON.stringify({status: "unknown"});
    var pageCountNode = document.querySelector("#sp_1_pager");
    var pageCount = pageCountNode ? Number(pageCountNode.textContent.trim()) : NaN;
    if (!Number.isInteger(pageCount)) return JSON.stringify({status: "unknown"});
    if (pageCount > 1) return JSON.stringify({status: "partial"});
    if (grid.innerText.indexOf("没有符合条件记录") >= 0 && pageCount === 0) {
        return JSON.stringify({status: "empty", scope: scope});
    }
    if (pageCount !== 1) return JSON.stringify({status: "unknown"});
    var fields = source === "EXAMS" ? ["kcmc", "kssj", "cdmc", "cdxqmc", "zwh", "ksmc"] :
        ["kcmc", "cj", "xf", "jd", "ksxz", "cjbz"];
    var rows = Array.from(grid.querySelectorAll("tr")).filter(function (row) {
        return row.id && row.querySelector("td[aria-describedby]");
    }).map(function (row) {
        var record = {};
        fields.forEach(function (field) {
            var cell = row.querySelector('td[aria-describedby="tabGrid_' + field + '"]');
            record[field] = cell ? cell.textContent.trim() : "";
        });
        return record;
    });
    return JSON.stringify({status: "ok", scope: scope, rows: rows});
})();
