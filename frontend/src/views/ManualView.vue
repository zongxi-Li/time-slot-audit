<!--
  文件职责：提供 TimeSlot 的快速操作说明和预约状态图例。
  接口：纯前端说明页，不读取或修改业务数据。
-->
<template>
  <div class="page manual-page">
    <header class="manual-hero">
      <div>
        <span class="page-eyebrow">Guide / Quick start</span>
        <h2 class="page-title">使用说明书</h2>
        <p class="page-subtitle">
          用预约看板找到合适的时间，提交会议室预约，并在这里快速读懂每一种状态。
        </p>
      </div>
      <div class="manual-hero-note" aria-label="使用提示">
        <span class="manual-hero-note-dot" />
        <span>先看颜色，再看状态</span>
      </div>
    </header>

    <section class="guide-section" aria-labelledby="quick-start-title">
      <div class="section-heading">
        <div>
          <span class="section-kicker">01 / START HERE</span>
          <h3 id="quick-start-title">三步完成一次预约</h3>
        </div>
        <p>从看板开始，信息会沿着预约流程自然流转。</p>
      </div>

      <div class="steps-grid">
        <article class="step-card panel">
          <span class="step-number">01</span>
          <h4>选择时间和会议室</h4>
          <p>进入“预约看板”，切换日视图或周视图，先筛选会议室，再点击或拖动空闲时段。</p>
          <span class="step-caption">看板 · 日视图 / 周视图</span>
        </article>
        <article class="step-card panel">
          <span class="step-number">02</span>
          <h4>填写并提交预约</h4>
          <p>点击“新建预约”，填写会议主题、时间、参会人数和备注，确认无误后提交。</p>
          <span class="step-caption">预约表单 · 提交申请</span>
        </article>
        <article class="step-card panel">
          <span class="step-number">03</span>
          <h4>查看状态和详情</h4>
          <p>点击看板中的预约卡片查看详情；在“我的预约”里修改、取消或跟进自己的预约。</p>
          <span class="step-caption">我的预约 · 预约详情</span>
        </article>
      </div>
    </section>

    <section class="guide-section" aria-labelledby="reservation-colors-title">
      <div class="section-heading">
        <div>
          <span class="section-kicker">02 / READ THE BOARD</span>
          <h3 id="reservation-colors-title">预约看板的颜色</h3>
        </div>
        <p>这里的颜色表示预约状态，不是会议室的等级或容量。</p>
      </div>

      <div class="legend-grid">
        <article class="legend-card panel">
          <div class="legend-swatch legend-swatch--confirmed" aria-hidden="true">
            <span />
          </div>
          <div>
            <h4>蓝色 · 已确认</h4>
            <p>预约已通过或正在占用该时段，其他人不能再预约同一时间。</p>
          </div>
        </article>
        <article class="legend-card panel">
          <div class="legend-swatch legend-swatch--pending" aria-hidden="true">
            <span />
          </div>
          <div>
            <h4>琥珀色 · 待审核</h4>
            <p>预约已经提交，但还没有完成审批；最终是否生效以审批结果为准。</p>
          </div>
        </article>
        <article class="legend-card panel">
          <div class="legend-swatch legend-swatch--inactive" aria-hidden="true">
            <span />
          </div>
          <div>
            <h4>灰色虚线 · 已取消 / 已驳回</h4>
            <p>这条记录不再占用时段，标题会带删除线，实际时间通常可以重新预约。</p>
          </div>
        </article>
        <article class="legend-card panel">
          <div class="legend-swatch legend-swatch--mine" aria-hidden="true">
            <span />
          </div>
          <div>
            <h4>外圈高亮 · 我的预约</h4>
            <p>蓝色或琥珀色卡片外圈更明显，表示这是当前登录用户创建的预约。</p>
          </div>
        </article>
      </div>
    </section>

    <section class="guide-section" aria-labelledby="room-status-title">
      <div class="section-heading">
        <div>
          <span class="section-kicker">03 / ROOM STATUS</span>
          <h3 id="room-status-title">会议室列表的状态</h3>
        </div>
        <p>在“会议室”栏目中，圆点颜色表示会议室当前是否适合使用。</p>
      </div>

      <div class="room-status-row panel">
        <div class="room-status-item">
          <span class="status-dot status-dot--available" />
          <div><strong>绿色</strong><span>可用</span></div>
        </div>
        <div class="room-status-item">
          <span class="status-dot status-dot--in-use" />
          <div><strong>橙色</strong><span>使用中</span></div>
        </div>
        <div class="room-status-item">
          <span class="status-dot status-dot--maintenance" />
          <div><strong>深黄色</strong><span>维护中</span></div>
        </div>
        <div class="room-status-item">
          <span class="status-dot status-dot--disabled" />
          <div><strong>灰色</strong><span>已停用</span></div>
        </div>
      </div>
    </section>

    <aside class="manual-tip panel">
      <div class="tip-mark">Tip</div>
      <div>
        <strong>一个小提示</strong>
        <p>如果预约卡片已经变成灰色，不要只看标题，打开详情确认原因；取消或驳回的预约不会继续占用时间。</p>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.manual-page {
  overflow-y: auto;
}

.manual-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 28px;
  border-bottom: 1px solid var(--border-light);
}

.manual-hero .page-subtitle {
  max-width: 620px;
  margin-bottom: 0;
}

.manual-hero-note {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 9px;
  padding: 10px 13px;
  color: var(--text-secondary);
  border: 1px solid var(--border-light);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
  font-size: 12px;
  font-weight: 600;
}

.manual-hero-note-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-primary);
  box-shadow: 0 0 0 4px rgba(0, 113, 227, 0.1);
}

.guide-section {
  margin-top: 30px;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 14px;
}

.section-kicker {
  color: var(--el-color-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.15em;
}

.section-heading h3 {
  margin: 6px 0 0;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.035em;
}

.section-heading p {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
}

.steps-grid,
.legend-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.step-card,
.legend-card {
  border-color: rgba(255, 255, 255, 0.88);
  background: rgba(255, 255, 255, 0.76);
  box-shadow: 0 8px 24px rgba(29, 29, 31, 0.045);
}

.step-card {
  min-height: 208px;
  padding: 22px;
}

.step-number {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 24px;
  border-radius: 7px;
  color: var(--el-color-primary);
  background: rgba(0, 113, 227, 0.09);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.05em;
}

.step-card h4,
.legend-card h4 {
  margin: 20px 0 8px;
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.step-card p,
.legend-card p,
.manual-tip p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 22px;
  text-wrap: pretty;
}

.step-caption {
  display: block;
  margin-top: 18px;
  color: var(--text-muted);
  font-size: 11px;
  letter-spacing: 0.03em;
}

.legend-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.legend-card {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  min-height: 124px;
  padding: 20px;
}

.legend-card h4 {
  margin: 1px 0 7px;
  font-size: 14px;
}

.legend-swatch {
  position: relative;
  flex: 0 0 74px;
  width: 74px;
  height: 58px;
  margin-top: 2px;
  padding: 9px;
  border-radius: 10px;
}

.legend-swatch span {
  display: block;
  width: 48px;
  height: 8px;
  border-radius: 4px;
  background: currentColor;
  box-shadow: 0 12px 0 -1px currentColor, 0 24px 0 -1px currentColor;
  opacity: 0.78;
}

.legend-swatch--confirmed {
  color: #3f6ea6;
  background: #d2e6ff;
  border: 1px solid rgba(0, 113, 227, 0.38);
}

.legend-swatch--pending {
  color: #a37e3c;
  background: #ffe8c4;
  border: 1px solid rgba(202, 138, 25, 0.45);
}

.legend-swatch--inactive {
  color: #a0a0a5;
  background: rgba(29, 29, 31, 0.055);
  border: 1px dashed rgba(29, 29, 31, 0.28);
}

.legend-swatch--inactive span {
  text-decoration: line-through;
}

.legend-swatch--mine {
  color: #3f6ea6;
  background: #d2e6ff;
  border: 2px solid rgba(0, 113, 227, 0.5);
  box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.74);
}

.room-status-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 17px 20px;
  border-color: rgba(255, 255, 255, 0.88);
  background: rgba(255, 255, 255, 0.72);
}

.room-status-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.status-dot {
  flex: 0 0 9px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.status-dot--available { background: #17b26a; }
.status-dot--in-use { background: #f79009; }
.status-dot--maintenance { background: #d97706; }
.status-dot--disabled { background: #86868b; }

.room-status-item div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.room-status-item strong {
  font-size: 13px;
}

.room-status-item span:not(.status-dot) {
  color: var(--text-muted);
  font-size: 11px;
}

.manual-tip {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-top: 30px;
  padding: 18px 20px;
  border-color: rgba(0, 113, 227, 0.12);
  background: rgba(237, 246, 253, 0.76);
}

.tip-mark {
  flex: 0 0 auto;
  padding: 4px 7px;
  border-radius: 6px;
  color: var(--el-color-primary);
  background: rgba(0, 113, 227, 0.1);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.manual-tip strong {
  display: block;
  margin-bottom: 4px;
  font-size: 13px;
}

@media (max-width: 980px) {
  .manual-hero,
  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .steps-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .legend-grid,
  .room-status-row {
    grid-template-columns: 1fr;
  }

  .manual-hero-note {
    display: none;
  }
}
</style>
