const fs = require('fs');
const path = require('path');

const template = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/public/index.html'),
  'utf-8'
);
const inlineScript = template.match(/<script>\s*([\s\S]*?)\s*<\/script>/)[1];

describe('homepage slogan', () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <div id="slogan-display">
        <p id="slogan-quote"></p>
        <p id="slogan-author"></p>
      </div>
    `;
    vi.spyOn(window, 'setInterval').mockImplementation(() => 0);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  test('renders a non-empty initial slogan', () => {
    expect(() => eval(inlineScript)).not.toThrow();
    expect(document.getElementById('slogan-quote').textContent).not.toBe('');
    expect(document.getElementById('slogan-author').textContent).not.toBe('');
  });
});
