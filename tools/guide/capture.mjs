#!/usr/bin/env node
/*
 * Chụp ảnh màn hình cho trang Hướng dẫn sử dụng: web/guide/<phân hệ>/*.png.
 *
 * VÌ SAO CÓ FILE NÀY. Ảnh hướng dẫn phải là ảnh THẬT của app, mà giao diện còn
 * đổi. Chụp tay thì mỗi lần sửa một trang là một loạt ảnh lỗi thời không ai
 * hay. Script này dựng lại toàn bộ ảnh của một phân hệ bằng một lệnh; số khoanh
 * đỏ được vẽ thẳng lên trang trước khi chụp, nên cùng một ảnh dùng được cho cả
 * trang hướng dẫn trong app lẫn file Word (Report6).
 *
 * CÁCH CHẠY (Node 22+, KHÔNG cần npm):
 *   node tools/guide/capture.mjs customer
 *   node tools/guide/capture.mjs customer --base http://127.0.0.1:8081/POSCS --only 03
 * Kịch bản từng phân hệ nằm ở tools/guide/shots/<phân hệ>.mjs.
 *
 * CHỈ TRỎ VÀO TOMCAT THỬ CHẠY TRÊN BẢN SAO poscs_db. Một số ảnh cần bấm lưu thật
 * (ảnh thông báo lỗi, ảnh sau khi lưu), và tài khoản dưới đây là tài khoản thử
 * -- cùng bộ với tools/testdoc/run_blackbox.py.
 *
 * TRÌNH DUYỆT. Dùng Chrome/Edge đã cài trên máy ở chế độ ẩn (--headless=new),
 * điều khiển qua Chrome DevTools Protocol bằng WebSocket có sẵn của Node --
 * không Puppeteer/Playwright, không tải thêm gì. Đổi trình duyệt bằng biến môi
 * trường CHROME=<đường dẫn>.
 */

import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { setTimeout as sleep } from 'node:timers/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

const ACCOUNTS = {
    admin: 'Admin@123',
    sales1: 'Poscs@123',
    sales4: 'Poscs@123',
    sales5: 'Poscs@123',
    cskh1: 'Poscs@123',
    kythuat1: 'Poscs@123'
};

const CHROME_CANDIDATES = [
    process.env.CHROME,
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
    'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    '/usr/bin/google-chrome',
    '/usr/bin/chromium'
].filter(Boolean);

// ----------------------------------------------------------------------
// Chrome DevTools Protocol qua WebSocket
// ----------------------------------------------------------------------

class Cdp {
    constructor(wsUrl) {
        this.ws = new WebSocket(wsUrl);
        this.seq = 0;
        this.pending = new Map();
        this.listeners = new Set();
    }

    async open() {
        await new Promise((resolve, reject) => {
            this.ws.addEventListener('open', resolve, { once: true });
            this.ws.addEventListener('error', reject, { once: true });
        });
        this.ws.addEventListener('message', (ev) => {
            const msg = JSON.parse(ev.data);
            if (msg.id && this.pending.has(msg.id)) {
                const { resolve, reject } = this.pending.get(msg.id);
                this.pending.delete(msg.id);
                if (msg.error) {
                    reject(new Error(msg.error.message));
                } else {
                    resolve(msg.result);
                }
            } else if (msg.method) {
                this.listeners.forEach((fn) => fn(msg));
            }
        });
    }

    send(method, params = {}) {
        const id = ++this.seq;
        this.ws.send(JSON.stringify({ id, method, params }));
        return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }));
    }

    // Promise chờ MỘT sự kiện. Phải tạo TRƯỚC khi gây ra sự kiện đó (vd. trước
    // Page.navigate), nếu không có thể lỡ mất nó.
    once(method, timeoutMs = 30000) {
        return new Promise((resolve, reject) => {
            const fn = (msg) => {
                if (msg.method === method) {
                    this.listeners.delete(fn);
                    clearTimeout(timer);
                    resolve(msg.params);
                }
            };
            const timer = setTimeout(() => {
                this.listeners.delete(fn);
                reject(new Error('Hết giờ chờ ' + method));
            }, timeoutMs);
            this.listeners.add(fn);
        });
    }

    close() {
        this.ws.close();
    }
}

// ----------------------------------------------------------------------
// Thao tác trên trang -- kịch bản chỉ dùng những hàm này
// ----------------------------------------------------------------------

class Page {
    constructor(cdp, base) {
        this.cdp = cdp;
        this.base = base;
    }

    async eval(expression) {
        const r = await this.cdp.send('Runtime.evaluate', {
            expression, awaitPromise: true, returnByValue: true
        });
        if (r.exceptionDetails) {
            const e = r.exceptionDetails;
            throw new Error('Lỗi JS trên trang: ' + ((e.exception && e.exception.description) || e.text));
        }
        return r.result.value;
    }

    // Đi tới đường dẫn TRONG app (vd. "/customer?kind=buyer") và chờ trang nạp
    // xong, cộng thêm một nhịp cho các lệnh fetch lúc mở trang (xã/phường...).
    async goto(url) {
        const loaded = this.cdp.once('Page.loadEventFired');
        await this.cdp.send('Page.navigate', { url: this.base + url });
        await loaded;
        await this.settle();
    }

    async settle(ms = 600) {
        await sleep(ms);
    }

    async waitFor(selector, timeoutMs = 10000) {
        const until = Date.now() + timeoutMs;
        while (Date.now() < until) {
            if (await this.eval(`!!document.querySelector(${JSON.stringify(selector)})`)) {
                return;
            }
            await sleep(150);
        }
        throw new Error('Không thấy phần tử ' + selector);
    }

    async click(selector) {
        await this.need(selector);
        await this.eval(`document.querySelector(${JSON.stringify(selector)}).click()`);
        await this.settle(400);
    }

    // Bấm một thứ làm CHUYỂN TRANG (nút submit, link) rồi chờ trang mới nạp xong.
    async clickAndWait(selector) {
        await this.need(selector);
        const loaded = this.cdp.once('Page.loadEventFired');
        await this.eval(`document.querySelector(${JSON.stringify(selector)}).click()`);
        await loaded;
        await this.settle();
    }

    // Gõ giá trị vào ô rồi phát input + change, để JS của trang phản ứng như
    // khi người dùng gõ thật (vd. ô tiền tự chèn dấu chấm).
    async type(selector, text) {
        await this.need(selector);
        await this.eval(`(() => {
            const el = document.querySelector(${JSON.stringify(selector)});
            el.value = ${JSON.stringify(text)};
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
        })()`);
    }

    // Chọn trong ô select theo value, hoặc theo chữ hiển thị nếu không khớp value.
    async select(selector, valueOrText) {
        await this.need(selector);
        const ok = await this.eval(`(() => {
            const el = document.querySelector(${JSON.stringify(selector)});
            const want = ${JSON.stringify(String(valueOrText))};
            const opt = Array.from(el.options).find(o => o.value === want)
                || Array.from(el.options).find(o => o.textContent.trim() === want);
            if (!opt) { return false; }
            el.value = opt.value;
            el.dispatchEvent(new Event('change', { bubbles: true }));
            return true;
        })()`);
        if (!ok) {
            throw new Error(`Ô ${selector} không có lựa chọn "${valueOrText}"`);
        }
        await this.settle(500);
    }

    async need(selector) {
        if (!await this.eval(`!!document.querySelector(${JSON.stringify(selector)})`)) {
            throw new Error('Không thấy phần tử ' + selector);
        }
    }
}

// ----------------------------------------------------------------------
// Đăng nhập, khoanh số, chụp
// ----------------------------------------------------------------------

async function login(page, user) {
    if (!(user in ACCOUNTS)) {
        throw new Error('Chưa khai mật khẩu tài khoản thử ' + user + ' trong ACCOUNTS');
    }
    // Mở login.jsp là đủ đăng xuất người trước: AuthenticationFilter huỷ phiên
    // hiện có ngay khi có request tới trang này.
    await page.goto('/login.jsp');
    const loaded = page.cdp.once('Page.loadEventFired');
    await page.eval(`(() => {
        document.querySelector('[name=username]').value = ${JSON.stringify(user)};
        document.querySelector('[name=password]').value = ${JSON.stringify(ACCOUNTS[user])};
        document.getElementById('loginForm').submit();
    })()`);
    await loaded;
    await page.settle(300);
    const where = await page.eval('location.pathname');
    if (where.endsWith('/login.jsp') || where.endsWith('/changePassword.jsp')) {
        throw new Error(`Đăng nhập ${user} không vào được app (dừng ở ${where})`);
    }
}

// Vẽ khung + số đỏ lên các phần tử cần chỉ. marks: [{ sel, n, text?, pad? }]
// -- có text thì lấy phần tử ĐẦU TIÊN khớp sel mà chữ bên trong chứa text
// (cho những khối không có id riêng, vd. tiêu đề mục "Người liên hệ"). Trả
// về các mark không tìm thấy -- kịch bản sai thì phải báo, đừng chụp ảnh
// thiếu số.
function marksScript(marks) {
    return `(() => {
        const old = document.getElementById('__guideMarks');
        if (old) { old.remove(); }
        const layer = document.createElement('div');
        layer.id = '__guideMarks';
        layer.style.cssText = 'position:absolute;left:0;top:0;width:0;height:0;z-index:2147483647;pointer-events:none;';
        document.body.appendChild(layer);
        const missing = [];
        for (const m of ${JSON.stringify(marks)}) {
            const el = m.text
                ? Array.from(document.querySelectorAll(m.sel)).find((e) => e.textContent.includes(m.text))
                : document.querySelector(m.sel);
            if (!el) { missing.push(m.text ? m.sel + ' "' + m.text + '"' : m.sel); continue; }
            const r = el.getBoundingClientRect();
            const pad = m.pad == null ? 5 : m.pad;
            const x = r.left + window.scrollX - pad;
            const y = r.top + window.scrollY - pad;
            const box = document.createElement('div');
            box.style.cssText = 'position:absolute;box-sizing:border-box;border:3px solid #e11d48;border-radius:9px;'
                + 'box-shadow:0 0 0 3px rgba(225,29,72,.16);'
                + 'left:' + x + 'px;top:' + y + 'px;width:' + (r.width + 2 * pad) + 'px;height:' + (r.height + 2 * pad) + 'px;';
            const badge = document.createElement('div');
            badge.textContent = m.n;
            badge.style.cssText = 'position:absolute;width:26px;height:26px;border-radius:50%;background:#e11d48;color:#fff;'
                + 'font:700 14px/26px Inter,Arial,sans-serif;text-align:center;box-shadow:0 2px 6px rgba(0,0,0,.35);'
                + 'left:' + Math.max(2, x - 13) + 'px;top:' + Math.max(2, y - 13) + 'px;';
            // n rỗng = chỉ khoanh, không đánh số (ảnh không có danh sách bước đi kèm).
            if (m.n == null || m.n === '') { layer.append(box); } else { layer.append(box, badge); }
        }
        return missing;
    })()`;
}

// Vùng chụp: null = đúng khung nhìn; 'full' = cả trang; selector hoặc mảng
// selector = hình chữ nhật bao quanh (hợp của) các phần tử đó, nới thêm lề.
async function clipOf(page, clip, margin = 16) {
    if (!clip) {
        return null;
    }
    if (clip === 'full') {
        const m = await page.cdp.send('Page.getLayoutMetrics');
        const size = m.cssContentSize || m.contentSize;
        return { x: 0, y: 0, width: Math.ceil(size.width), height: Math.ceil(size.height), scale: 1 };
    }
    const selectors = Array.isArray(clip) ? clip : [clip];
    const rect = await page.eval(`(() => {
        let l = Infinity, t = Infinity, r = -Infinity, b = -Infinity;
        for (const s of ${JSON.stringify(selectors)}) {
            const el = document.querySelector(s);
            if (!el) { return { missing: s }; }
            const q = el.getBoundingClientRect();
            l = Math.min(l, q.left + scrollX); t = Math.min(t, q.top + scrollY);
            r = Math.max(r, q.right + scrollX); b = Math.max(b, q.bottom + scrollY);
        }
        return { l, t, r, b };
    })()`);
    if (rect.missing) {
        throw new Error('Không thấy vùng chụp ' + rect.missing);
    }
    const x = Math.max(0, Math.floor(rect.l - margin));
    const y = Math.max(0, Math.floor(rect.t - margin));
    return {
        x, y,
        width: Math.ceil(rect.r + margin) - x,
        height: Math.ceil(rect.b + margin) - y,
        scale: 1
    };
}

async function capture(page, shot, outFile) {
    if (shot.marks && shot.marks.length) {
        const missing = await page.eval(marksScript(shot.marks));
        if (missing.length) {
            throw new Error('Không thấy phần tử để khoanh số: ' + missing.join(', '));
        }
    }
    const clip = await clipOf(page, shot.clip, shot.margin);
    const params = { format: 'png' };
    if (clip) {
        params.clip = clip;
        params.captureBeyondViewport = true;
    }
    const { data } = await page.cdp.send('Page.captureScreenshot', params);
    writeFileSync(outFile, Buffer.from(data, 'base64'));
}

// ----------------------------------------------------------------------
// Chạy
// ----------------------------------------------------------------------

function args() {
    const out = { module: null, base: 'http://127.0.0.1:8081/POSCS', only: null, out: null };
    const a = process.argv.slice(2);
    for (let i = 0; i < a.length; i++) {
        if (a[i] === '--base') {
            out.base = a[++i];
        } else if (a[i] === '--only') {
            out.only = a[++i];
        } else if (a[i] === '--out') {
            out.out = a[++i];
        } else {
            out.module = a[i];
        }
    }
    if (!out.module) {
        console.error('Dùng: node tools/guide/capture.mjs <phân hệ | đường dẫn kịch bản .mjs> [--base URL] [--only tiền-tố-tên-ảnh] [--out thư-mục]');
        process.exit(2);
    }
    return out;
}

async function launchChrome() {
    const exe = CHROME_CANDIDATES.find((p) => existsSync(p));
    if (!exe) {
        throw new Error('Không tìm thấy Chrome/Edge. Đặt biến CHROME=<đường dẫn tới chrome.exe>.');
    }
    const profile = mkdtempSync(path.join(tmpdir(), 'poscs-guide-'));
    const proc = spawn(exe, [
        '--headless=new', '--remote-debugging-port=0', '--user-data-dir=' + profile,
        '--no-first-run', '--no-default-browser-check', '--disable-extensions',
        // KHÔNG ẩn thanh cuộn: bảng danh sách cố ý cuộn ngang và luôn hiện thanh
        // cuộn (xem listcustomer.jsp) -- ảnh phải giống đúng cái người dùng thấy.
        '--force-device-scale-factor=1', '--lang=vi-VN',
        'about:blank'
    ], { stdio: 'ignore' });
    // Cổng 0 = để Chrome tự chọn cổng trống; nó ghi cổng vào file này.
    const portFile = path.join(profile, 'DevToolsActivePort');
    for (let i = 0; i < 100 && !existsSync(portFile); i++) {
        await sleep(100);
    }
    const port = readFileSync(portFile, 'utf8').split('\n')[0].trim();
    const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
    const target = targets.find((t) => t.type === 'page');
    const cdp = new Cdp(target.webSocketDebuggerUrl);
    await cdp.open();
    const stop = () => {
        cdp.close();
        proc.kill();
        // Chrome giữ file trong hồ sơ một lúc sau khi tắt -- xoá hỏng thì thôi.
        setTimeout(() => { try { rmSync(profile, { recursive: true, force: true }); } catch { /* bỏ qua */ } }, 1500);
    };
    return { cdp, stop };
}

async function main() {
    const opt = args();
    // Tên phân hệ -> tools/guide/shots/<tên>.mjs, ảnh ra web/guide/<tên>/. Cũng nhận
    // thẳng một file kịch bản .mjs kèm --out (vd. chụp thử trang hướng dẫn để
    // duyệt mà không bỏ ảnh vào web/).
    const isFile = opt.module.endsWith('.mjs');
    const scenarioFile = isFile ? path.resolve(opt.module) : path.join(ROOT, 'tools', 'guide', 'shots', opt.module + '.mjs');
    const { default: scenario } = await import(pathToFileURL(scenarioFile).href);
    const outDir = opt.out ? path.resolve(opt.out)
        : path.join(ROOT, 'web', 'guide', isFile ? path.basename(opt.module, '.mjs') : opt.module);
    mkdirSync(outDir, { recursive: true });

    const { cdp, stop } = await launchChrome();
    let failed = 0;
    try {
        await cdp.send('Page.enable');
        await cdp.send('Runtime.enable');
        const page = new Page(cdp, opt.base);
        let currentUser = null;
        for (const shot of scenario.shots) {
            if (opt.only && !shot.file.startsWith(opt.only)) {
                continue;
            }
            const vp = shot.viewport || scenario.viewport || { width: 1440, height: 900 };
            await cdp.send('Emulation.setDeviceMetricsOverride', {
                width: vp.width, height: vp.height, deviceScaleFactor: 1, mobile: false
            });
            try {
                if (shot.user !== currentUser) {
                    await login(page, shot.user);
                    currentUser = shot.user;
                }
                if (shot.url) {
                    await page.goto(shot.url);
                }
                if (shot.before) {
                    await shot.before(page);
                }
                await capture(page, shot, path.join(outDir, shot.file));
                console.log('  đã chụp  ' + shot.file);
            } catch (err) {
                failed++;
                console.error('  LỖI     ' + shot.file + ': ' + err.message);
                currentUser = null; // phiên có thể đã hỏng -- lượt sau đăng nhập lại
            }
        }
    } finally {
        stop();
    }
    if (failed) {
        console.error(failed + ' ảnh chụp hỏng.');
        process.exit(1);
    }
}

main().catch((err) => {
    console.error(err);
    process.exit(1);
});
