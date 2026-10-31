(function () {
    "use strict";
    var source = "__CAMPUS_SOURCE__";
    var powerPage = source === "POWER" || source === "POWER_USAGE";
    // School scripts replace Array.prototype.filter/some with incompatible callback signatures.
    // Use indexed DOM loops in the page's own JavaScript world.
    function visible(node) {
        if (!node || node.type === "hidden") return false;
        for (var current = node; current && current.nodeType === 1; current = current.parentElement) {
            var style = getComputedStyle(current);
            if (style.display === "none" || style.visibility === "hidden") return false;
        }
        return true;
    }
    if (powerPage) {
        var activeUsage = document.querySelector("#tab4");
        var activeBalance = document.querySelector("#tab1");
        if (activeUsage && visible(activeUsage)) source = "POWER_USAGE";
        else if (activeBalance && visible(activeBalance)) source = "POWER";
    }
    function answer(value) {
        if (powerPage) value.source = source;
        return JSON.stringify(value);
    }
    var passwords = document.querySelectorAll("input[type=password]");
    for (var p = 0; p < passwords.length; p++) if (visible(passwords[p])) return answer({status: "login"});
    var text = (document.body && document.body.innerText) || "";
    var activePicker = document.querySelector(".picker-modal.modal-in");
    if (activePicker && visible(activePicker)) return answer({status: "loading"});
    if (/正在请求数据|查询中(?:\.{3}|…)/.test(text)) return answer({status: "loading"});
    if (source === "POWER") {
        var room = document.querySelector("#picker-addr");
        var energy = document.querySelector("#sydl");
        if (!visible(room) || !visible(energy) || !room.value || !energy.value) return answer({status: "not_queried"});
        return answer({status: "ok", scope: room.value.trim(), remaining: energy.value.trim()});
    }
    if (source === "POWER_USAGE") {
        var panel = document.querySelector("#tab4");
        var usageRoom = document.querySelector("#picker-addr-view");
        var usageTable = panel && panel.querySelector("ul.table");
        if (!visible(panel) || !visible(usageRoom) || !usageRoom.value || !visible(usageTable)) return answer({status: "not_queried"});
        var usageRows = [];
        var items = usageTable.querySelectorAll("li");
        for (var u = 0; u < items.length; u++) {
            var dateCell = items[u].querySelector(".table_1");
            var valueCell = items[u].querySelector(".table_2");
            if (!!dateCell !== !!valueCell) return answer({status: "unknown"});
            var itemText = dateCell ? dateCell.textContent.trim() + " " + valueCell.textContent.trim() : items[u].innerText.trim();
            if (itemText && !/^日期\s+用量$/.test(itemText)) usageRows.push(itemText);
        }
        if (!usageRows.length) return answer({status: "not_queried"});
        return answer({status: "ok", scope: usageRoom.value.trim(), usage: usageRows});
    }
    if (source === "COURSES") {
        var table = document.querySelector("#kbgrid_table_0");
        if (!table) return answer({status: "not_queried"});
        var scopeMatch = (table.innerText || table.textContent).match(/\d{4}[-—]\d{4}学年第\d学期/);
        if (!scopeMatch) return answer({status: "unknown"});
        var cells = [];
        var unfixed = [];
        var courses = [];
        var mappedBlocks = 0;
        var structuredComplete = true;
        function plainLines(node) {
            var result = "";
            function visit(current) {
                if (current.nodeType === 3) { result += current.nodeValue; return; }
                if (current.nodeType !== 1) return;
                var boundary = /^(P|DIV|BR)$/.test(current.tagName);
                if (boundary) result += "\n";
                for (var n = 0; n < current.childNodes.length; n++) visit(current.childNodes[n]);
                if (boundary) result += "\n";
            }
            visit(node);
            return result.trim();
        }
        var allCells = table.querySelectorAll("td");
        for (var c = 0; c < allCells.length; c++) {
            var cell = allCells[c];
            var cellText = plainLines(cell);
            if (/^[1-7]-\d+$/.test(cell.id) && cellText) {
                var day = Number(cell.id.split("-")[0]);
                cells.push({day: day, text: cellText});
                var blocks = cell.querySelectorAll(".timetable_con");
                mappedBlocks += blocks.length;
                for (var b = 0; b < blocks.length; b++) {
                    var title = blocks[b].querySelector("span.title");
                    var paragraphs = [];
                    var notes = [];
                    for (var k = 0; k < blocks[b].children.length; k++) {
                        var child = blocks[b].children[k];
                        var childText = child.textContent.trim();
                        if (child.tagName === "P" && childText) paragraphs.push(childText);
                        else if (child !== title && childText) notes.push(childText);
                    }
                    if (!title || !title.textContent.trim() || !paragraphs.length) { structuredComplete = false; continue; }
                    courses.push({day: day, name: title.textContent.trim(), rule: paragraphs[0], details: paragraphs.slice(1).concat(notes)});
                }
            } else if (!cell.id && /实践课程[：:]|其它课程[：:]|其他课程[：:]/.test(cellText)) {
                unfixed.push(cellText);
            }
        }
        var diagnostics = {tableCells: allCells.length, matchedCells: cells.length,
            courseBlocks: table.querySelectorAll(".timetable_con").length, mappedBlocks: mappedBlocks, unfixed: unfixed.length};
        if (mappedBlocks !== diagnostics.courseBlocks) return answer({status: "unknown", diagnostics: diagnostics});
        if (!cells.length && !unfixed.length) return answer({status: "not_queried", diagnostics: diagnostics});
        var capture = {status: "ok", scope: scopeMatch[0].replace("—", "-"), cells: cells, unfixed: unfixed, diagnostics: diagnostics};
        if (structuredComplete && courses.length === diagnostics.courseBlocks && courses.length > 0) capture.courses = courses;
        return answer(capture);
    }
    if (text.indexOf("请选择筛选条件") >= 0) return answer({status: "not_queried"});
    var yearSelect = document.querySelector("select#xnm, select[name=xnm]");
    var termSelect = document.querySelector("select#xqm, select[name=xqm]");
    function selectedText(select) {
        return select && select.selectedOptions.length === 1 ? select.selectedOptions[0].text.trim() : "";
    }
    var year = selectedText(yearSelect);
    var term = selectedText(termSelect);
    if (!/^\d{4}[-—]\d{4}$/.test(year) || !/^(?:第)?[123](?:学期)?$/.test(term)) return answer({status: "unknown"});
    var scope = year + " · 第" + term.replace(/\D/g, "") + "学期";
    var grid = document.querySelector("#tabGrid");
    if (!grid) return answer({status: "unknown"});
    var pageCountNode = document.querySelector("#sp_1_pager");
    var pageCount = pageCountNode ? Number(pageCountNode.textContent.trim()) : NaN;
    if (!Number.isInteger(pageCount)) return answer({status: "unknown"});
    if (pageCount > 1) return answer({status: "partial"});
    if (grid.innerText.indexOf("没有符合条件记录") >= 0 && pageCount === 0) return answer({status: "empty", scope: scope});
    if (pageCount !== 1) return answer({status: "unknown"});
    var fields = source === "EXAMS" ? ["kcmc", "kssj", "cdmc", "cdxqmc", "zwh", "ksmc"] : ["kcmc", "cj", "xf", "jd", "ksxz", "cjbz"];
    var rows = [];
    var tableRows = grid.querySelectorAll("tr");
    for (var r = 0; r < tableRows.length; r++) {
        var row = tableRows[r];
        if (!row.id || !row.querySelector("td[aria-describedby]")) continue;
        var record = {};
        for (var f = 0; f < fields.length; f++) {
            var field = fields[f];
            var valueCell = row.querySelector('td[aria-describedby="tabGrid_' + field + '"]');
            record[field] = valueCell ? valueCell.textContent.trim() : "";
        }
        rows.push(record);
    }
    return answer({status: "ok", scope: scope, rows: rows});
})();
