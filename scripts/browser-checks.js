// Run using Playwright CLI: run-code --filename=scripts/browser-checks.js
async (page) => {
  await page.clock.setFixedTime(new Date('2026-09-29T00:00:00+08:00'));
  await page.goto('http://127.0.0.1:1420');
  const text = async (selector, expected) => {
    const actual = await page.locator(selector).innerText();
    if (actual !== expected) throw new Error(`${selector}: ${actual} != ${expected}`);
  };
  const fits = async () => {
    const d = await page.evaluate(() => ({w: innerWidth, h: innerHeight, sw: document.documentElement.scrollWidth, sh: document.documentElement.scrollHeight}));
    if (d.sw > d.w || d.sh > d.h) throw new Error('Overflow: ' + JSON.stringify(d));
  };
  const settings = async () => {
    const opened = page.waitForEvent('popup');
    await page.getByRole('button', { name: '设置', exact: true }).click();
    const panel = await opened;
    await panel.getByRole('textbox', { name: '计划起床' }).waitFor();
    return panel;
  };
  for (const [size, width] of [['six', 240], ['four', 160]]) {
    const panel = await settings();
    await panel.getByRole('textbox', { name: '计划起床' }).fill('10:00');
    await panel.getByRole('textbox', { name: '计划入睡' }).fill('10:00');
    await panel.getByRole('button', { name: '保存设置' }).click();
    if (await panel.locator('#error').innerText() !== '起床时间与入睡时间不能相同。') throw new Error('Missing validation');
    await panel.getByRole('textbox', { name: '计划入睡' }).fill('02:00');
    await panel.getByRole('combobox', { name: '组件大小' }).selectOption(size);
    if (!(await panel.getByRole('checkbox', { name: '登录系统后自动启动' }).isDisabled())) throw new Error('Browser must not offer native autostart');
    await panel.getByRole('button', { name: '保存设置' }).click();
    await page.reload();
    await page.setViewportSize({ width, height: 128 });
    await page.locator('#personal-time').filter({ hasText: '21:00' }).waitFor();
    await text('#percent', '87.50%'); await fits();
    if (await page.locator('#remaining, #schedule-label').count()) throw new Error('Removed lines remain');
    for (const colorScheme of ['light', 'dark']) {
      await page.emulateMedia({ colorScheme });
      const style = await page.locator('#app').evaluate(el => ({ color: getComputedStyle(el).color, radius: getComputedStyle(el).borderRadius, bg: getComputedStyle(el).backgroundColor }));
      if (style.color !== 'rgb(0, 0, 0)' || style.radius !== '8px' || style.bg !== 'rgba(0, 0, 0, 0)') throw new Error(JSON.stringify(style));
      await fits();
      await page.screenshot({ path: `output/playwright/glass-${size}-${colorScheme}.png` });
    }
  }
  const panel = await settings();
  await panel.getByRole('textbox', { name: '计划起床' }).fill('08:00');
  await panel.getByRole('textbox', { name: '计划入睡' }).fill('00:00');
  await panel.getByRole('button', { name: '保存设置' }).click();
  await page.locator('#personal-time').filter({ hasText: '24:00' }).waitFor();
  await text('#percent', '100.00%');
  if (!(await page.locator('#rest-note').innerText()).includes('上一清醒日已结束 · 下次起床')) throw new Error('Missing rest context');
  await page.setViewportSize({ width: 160, height: 158 });
  await fits();
  await page.screenshot({ path: 'output/playwright/glass-four-rest.png' });
  for (const [date, personal, percent] of [
    ['2026-09-29T08:00:00+08:00', '00:00', '0.00%'],
    ['2026-09-29T12:00:00+08:00', '06:00', '25.00%'],
    ['2026-09-29T23:59:59.999+08:00', '23:59', '99.99%'],
  ]) {
    await page.clock.setFixedTime(new Date(date));
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    await text('#personal-time', personal); await text('#percent', percent); await fits();
    if (await page.locator('#rest-note').isVisible()) throw new Error('Rest text in waking period');
  }
  return 'PASS: no system theme response, 8px corners, untinted CSS, removed lines, separate settings, validation, persistence, both footprints, rest, wake, midnight and rounding. Wallpaper and adaptive text need desktop verification.';
}
