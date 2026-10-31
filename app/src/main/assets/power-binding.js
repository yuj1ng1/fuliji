function (config) {
    "use strict";
    function answer(state) { return JSON.stringify({state: state}); }
    var query = new URL(location.href).searchParams;
    if (location.protocol !== "https:" || location.hostname !== "hqwx.fjut.edu.cn" ||
        (location.port && location.port !== "443") || location.pathname !== "/agent/app/index.php" ||
        query.getAll("i").length !== 1 || query.getAll("c").length !== 1 || query.getAll("do").length !== 1 || query.getAll("m").length !== 1 ||
        query.get("i") !== "67" || query.get("c") !== "entry" || query.get("do") !== "register" || query.get("m") !== "maihu_portal") return answer("blocked");
    function visible(node) {
        if (!node || node.type === "hidden") return false;
        for (var current = node; current && current.nodeType === 1; current = current.parentElement) {
            var style = getComputedStyle(current);
            if (style.display === "none" || style.visibility === "hidden") return false;
            var rect = current.getBoundingClientRect();
            if ((style.overflow === "hidden" || style.overflow === "clip" || style.overflow === "auto") && (rect.width < 1 || rect.height < 1)) return false;
        }
        var box = node.getBoundingClientRect();
        return box.width > 0 && box.height > 0;
    }
    var user = document.querySelector("#studentid");
    var pass = document.querySelector("#password");
    var phone = document.querySelector("#mobile");
    var code = document.querySelector("#code");
    var text = document.body ? document.body.innerText : "";
    if (!visible(user) || !visible(pass) || !visible(phone) || !visible(code) || text.indexOf("用户身份认证") < 0 || text.indexOf("立即绑定") < 0) return answer("unavailable");
    if (!config.inspectOnly && !document.documentElement.dataset.rongjiPowerFilled) {
        var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set;
        setter.call(user, config.username);
        setter.call(pass, config.password);
        user.dispatchEvent(new Event("input", {bubbles: true}));
        pass.dispatchEvent(new Event("input", {bubbles: true}));
        document.documentElement.dataset.rongjiPowerFilled = "yes";
    }
    // SMS requests, verification codes and the binding action are always controlled by the user.
    return answer("phone_required");
}
