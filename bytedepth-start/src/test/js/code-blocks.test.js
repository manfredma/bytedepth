const fs = require('fs');
const path = require('path');

const detailTemplate = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/public/posts/detail.html'),
  'utf-8'
);
const codeBlocksCss = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/css/code-blocks.css'),
  'utf-8'
);
const loadCodeBlocks = async () => {
    vi.resetModules();
    await import('../../main/resources/static/js/code-blocks.js');
};

test('article pages declare isolated code block assets', () => {
    expect(detailTemplate).toContain('/css/code-blocks.css');
    expect(detailTemplate).toContain('/js/code-blocks.js');
});

test('code block CSS is scoped to enhanced markup', () => {
    expect(codeBlocksCss).toMatch(/\.content \.bd-code-block/);
    expect(codeBlocksCss).toMatch(/\.content \.bd-code-tabs/);
    expect(codeBlocksCss).not.toMatch(/\.content pre\s*\{/);
});

test('ordinary code blocks are not touched', async () => {
    document.body.innerHTML = '<article id="post-article"><div class="content"><pre><code class="language-java">plain code</code></pre></div></article>';
    await loadCodeBlocks();

    expect(document.querySelector('.content pre').outerHTML)
        .toBe('<pre><code class="language-java">plain code</code></pre>');
});

test('pages without article content do not initialize code block behavior', async () => {
    document.body.innerHTML = '<main>普通页面</main>';
    await loadCodeBlocks();

    expect(document.querySelector('.bd-code-block')).toBeNull();
});

test('copy button copies only its enhanced code block', async () => {
    document.body.innerHTML = `
        <article id="post-article"><div class="content">
            <div class="bd-code-block">
                <div class="bd-code-block__header"><button class="bd-code-block__copy" type="button">复制</button></div>
                <div class="bd-code-block__body"><pre><code>copy me</code></pre></div>
            </div>
        </div></article>`;
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {writeText}});
    await loadCodeBlocks();

    document.querySelector('.bd-code-block__copy').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(writeText).toHaveBeenCalledWith('copy me');
    expect(document.querySelector('.bd-code-block__copy').textContent).toBe('已复制');
});

test('copy falls back to the document command when Clipboard API is unavailable', async () => {
    document.body.innerHTML = `
        <article id="post-article"><div class="content">
            <div class="bd-code-block">
                <div class="bd-code-block__header"><button class="bd-code-block__copy" type="button">复制</button></div>
                <div class="bd-code-block__body"><pre><code>fallback code</code></pre></div>
            </div>
        </div></article>`;
    Object.defineProperty(navigator, 'clipboard', {configurable: true, value: undefined});
    document.execCommand = vi.fn().mockReturnValue(true);
    await loadCodeBlocks();

    document.querySelector('.bd-code-block__copy').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(document.execCommand).toHaveBeenCalledWith('copy');
    expect(document.querySelector('.bd-code-block__copy').textContent).toBe('已复制');
});

test('copy reports a failure when both clipboard paths fail', async () => {
    document.body.innerHTML = `
        <article id="post-article"><div class="content">
            <div class="bd-code-block">
                <div class="bd-code-block__header"><button class="bd-code-block__copy" type="button">复制</button></div>
                <div class="bd-code-block__body"><pre><code>unavailable</code></pre></div>
            </div>
        </div></article>`;
    Object.defineProperty(navigator, 'clipboard', {configurable: true, value: undefined});
    document.execCommand = vi.fn().mockReturnValue(false);
    await loadCodeBlocks();

    document.querySelector('.bd-code-block__copy').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(document.querySelector('.bd-code-block__copy').textContent).toBe('复制失败');
});

test('copy handles an enhanced block without a code element', async () => {
    document.body.innerHTML = `
        <article id="post-article"><div class="content">
            <div class="bd-code-block">
                <div class="bd-code-block__header"><button class="bd-code-block__copy" type="button">复制</button></div>
            </div>
        </div></article>`;
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {writeText}});
    await loadCodeBlocks();

    document.querySelector('.bd-code-block__copy').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(writeText).toHaveBeenCalledWith('');
});

test('fold button hides and restores only the enhanced code body', async () => {
    document.body.innerHTML = `
        <article id="post-article"><div class="content">
            <pre><code>ordinary</code></pre>
            <div class="bd-code-block">
                <div class="bd-code-block__header"><button class="bd-code-block__toggle" type="button" aria-expanded="false">展开</button></div>
                <div class="bd-code-block__body"><pre><code>fold me</code></pre></div>
            </div>
        </div></article>`;
    await loadCodeBlocks();

    const block = document.querySelector('.bd-code-block');
    const toggle = block.querySelector('.bd-code-block__toggle');
    expect(block.querySelector('.bd-code-block__body').hidden).toBe(true);
    toggle.click();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(block.querySelector('.bd-code-block__body').hidden).toBe(false);
    expect(document.querySelector('.content > pre').hidden).toBe(false);
});

test('fold button remains usable when the enhanced body is absent', async () => {
    document.body.innerHTML = `
        <article id="post-article"><div class="content">
            <div class="bd-code-block">
                <div class="bd-code-block__header"><button class="bd-code-block__toggle" type="button" aria-expanded="false">展开</button></div>
            </div>
        </div></article>`;
    await loadCodeBlocks();

    const toggle = document.querySelector('.bd-code-block__toggle');
    toggle.click();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
});

test('tab buttons switch only their own panels and update ARIA state', async () => {
    document.body.innerHTML = `
        <article id="post-article"><div class="content">
            <div class="bd-code-tabs">
                <div class="bd-code-tabs__list" role="tablist">
                    <button class="bd-code-tabs__tab" type="button" role="tab" aria-selected="true">Java</button>
                    <button class="bd-code-tabs__tab" type="button" role="tab" aria-selected="false">Kotlin</button>
                </div>
                <div class="bd-code-tabs__panels">
                    <div class="bd-code-tabs__panel bd-code-tabs__panel--active" role="tabpanel" aria-hidden="false">java</div>
                    <div class="bd-code-tabs__panel" role="tabpanel" aria-hidden="true">kotlin</div>
                </div>
            </div>
        </div></article>`;
    await loadCodeBlocks();

    const tabs = document.querySelector('.bd-code-tabs');
    const buttons = tabs.querySelectorAll('.bd-code-tabs__tab');
    const panels = tabs.querySelectorAll('.bd-code-tabs__panel');
    buttons[1].click();

    expect(buttons[0].getAttribute('aria-selected')).toBe('false');
    expect(buttons[1].getAttribute('aria-selected')).toBe('true');
    expect(panels[0].hidden).toBe(true);
    expect(panels[1].hidden).toBe(false);
    expect(panels[1].getAttribute('aria-hidden')).toBe('false');
});

test('tab groups without a selected tab activate the first panel', async () => {
    document.body.innerHTML = `
        <article id="post-article"><div class="content">
            <div class="bd-code-tabs">
                <div class="bd-code-tabs__list" role="tablist">
                    <button class="bd-code-tabs__tab" type="button" role="tab" aria-selected="false">One</button>
                    <button class="bd-code-tabs__tab" type="button" role="tab" aria-selected="false">Two</button>
                </div>
                <div class="bd-code-tabs__panels">
                    <div class="bd-code-tabs__panel" role="tabpanel" aria-hidden="true">one</div>
                    <div class="bd-code-tabs__panel" role="tabpanel" aria-hidden="true">two</div>
                </div>
            </div>
        </div></article>`;
    await loadCodeBlocks();

    expect(document.querySelectorAll('.bd-code-tabs__tab')[0].getAttribute('aria-selected')).toBe('true');
    expect(document.querySelectorAll('.bd-code-tabs__panel')[0].hidden).toBe(false);
});
