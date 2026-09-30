const $ = id => document.getElementById(id);
let destinations = [], activeRegion = '전체', requestId = 0;
let pending = null;
function node(tag,className,text) { const n=document.createElement(tag); if(className)n.className=className; if(text!==undefined)n.textContent=text; return n; }
function sourceLink(source,label) {
  const a=node('a','source-link',label || source.title);
  try { const url=new URL(source.url); if(url.protocol!=='https:')return node('span','',a.textContent); a.href=url.href; } catch { return node('span','',a.textContent); }
  a.target='_blank'; a.rel='noopener noreferrer'; return a;
}
function setBusy(busy) {
  $('report').setAttribute('aria-busy',String(busy)); $('loading').hidden=!busy; $('analyze-button').disabled=busy || !destinations.length;
  $('analyze-button').textContent=busy?'단서 찾는 중…':'탐험 시작 →';
}
function renderResult(data) {
  const result=$('result'); result.replaceChildren();
  $('result-kind').textContent=data.kind==='live'?'AI 웹 검색 결과':data.kind==='empty'?'자료 없음':'검토 자료 예시 · 신규 검색 아님';
  if(data.kind==='empty') { const box=node('div','empty'); box.append(node('h3','',data.title),node('p','',data.summary)); const b=node('button','primary','속초 후기 30건 보기'); b.type='button'; b.addEventListener('click',()=>{ $('destination').value='sokcho'; $('period').querySelector('option[value="manual"]').disabled=false; $('period').value='manual'; document.querySelector('input[name="mode"][value="sample"]').checked=true; analyze(); });box.append(b);result.append(box);return; }
  const summary=node('article','summary-panel'); summary.append(node('span','confidence',data.confidence),node('h3','',data.title),node('p','summary-text',data.summary));
  const metadata=data.kind==='live'?'검색 결과에 연결된 출처 '+data.sources.length+'개 · 확인일 '+data.reviewedAt:'검토 자료 '+data.sources.length+'건 · 확인일 '+data.reviewedAt;
  summary.append(node('p','report-meta',metadata)); result.append(summary);
  if(data.prices.length) {
    result.append(node('h3','subheading','글에서 언급된 가격'));
    const grid=node('div','price-grid');
    data.prices.forEach(p=>{ const card=node('article','price-card');card.append(node('span','card-tag','온라인 가격 언급'),node('h4','',p.item),node('strong','amount',p.amount.toLocaleString('ko-KR')+'원'),node('p','',p.unit),node('p','small','글 작성일 '+p.date));const source=data.sources.find(s=>s.id===p.sourceId);if(source)card.append(sourceLink(source,'근거 글 보기'));grid.append(card); });
    result.append(grid,node('p','small','해당 글의 가격 언급입니다. 현재 판매 가격이나 같은 규격의 비교 가격으로 확정되지 않았습니다.'));
  }
  if(data.findings.length) {
    result.append(node('h3','subheading',data.kind==='live'?'근거가 연결된 문장':'자료에서 읽을 수 있는 이야기'));
    const list=node('div','findings');
    data.findings.forEach(f=>{const item=node('article','finding');item.append(node('p','',f.text)); const links=node('div','reference-links');f.sourceIds.forEach(id=>{const source=data.sources.find(s=>s.id===id);if(source)links.append(sourceLink(source,'출처 '+id));});item.append(links);list.append(item);});result.append(list);
  }
  if(data.kind!=='live') {
    const comparisons=node('div','comparison-note');comparisons.append(node('h3','', '비교 자료는 아직 부족해요'),node('p','', '일반 동네 · 다른 관광지 · 지난 분기와 비교하려면 같은 품목과 규격의 자료가 더 필요합니다.'));result.append(comparisons);
  }
  result.append(node('h3','subheading','직접 확인할 수 있는 근거'));
  const sources=node('div','sources');data.sources.forEach(s=>{const card=node('article','source-card');card.append(node('span','small',s.type+' · '+(s.date || '작성일 원문 확인')),sourceLink(s),node('p','small',new URL(s.url).hostname));sources.append(card);});result.append(sources);
  if(data.searchSuggestions) {const frame=node('iframe','search-suggestions');frame.title='Google 검색 제안';frame.setAttribute('sandbox','allow-popups allow-popups-to-escape-sandbox');frame.referrerPolicy='no-referrer';frame.srcdoc=data.searchSuggestions;result.append(frame);}
  result.append(node('p','limitations',data.limitation));
}

function reviewCard(source) {
  const card=node('article','source-card');
  card.append(node('span','small','후기 '+source.id+' · '+source.type+' · 작성일 '+source.date),sourceLink(source),node('p','review-excerpt',source.summary));
  return card;
}
function renderReviewPilot(data) {
  const result=$('result'); result.replaceChildren();
  $('result-kind').textContent='후기 30건 · 수동 검토';
  const summary=node('article','summary-panel');
  summary.append(node('span','confidence','반복된 경험 탐색 · 전체 시장 평가는 보류'),node('h3','',data.title),node('p','summary-text',data.summary));
  const stats=node('div','review-stats');
  [['30','검토 게시물'],['19 + 11','블로그 + 뽈레'],[String(data.currentQuarterCount),'2026년 3분기 글']].forEach(([value,label])=>{const item=node('div','review-stat');item.append(node('strong','',value),node('span','',label));stats.append(item);});
  summary.append(stats,node('p','report-meta','작성일 '+data.dateFrom+' ~ '+data.dateTo+' · 확인일 '+data.reviewedAt));
  summary.append(node('p','period-note','여러 분기를 섞은 통합 검토예요. 작성일과 실제 방문일은 다를 수 있어요.'));
  result.append(summary);
  const guide=node('aside','review-guide');
  guide.append(node('h3','','방문 전, 이렇게 활용해 보세요'),node('p','','가격 부담은 특정 품목의 경험으로 확인하고, 인기 점포는 대기 시간을 고려해 보세요. 술빵을 포장한다면 식은 뒤 맛에 대한 서로 다른 의견도 함께 읽어보세요.'));
  result.append(guide);
  result.append(node('h3','subheading','반복된 경험 · 펼쳐서 근거 확인'));
  const groups=[
    {label:'만족한 경험',tone:'positive',tags:['BV','SC','TO','SF']},
    {label:'아쉬웠던 경험',tone:'negative',tags:['PB','WB']},
    {label:'조건에 따라 달랐던 경험',tone:'mixed',tags:['WS','FS','CL','CO']}
  ];
  const grid=node('div','experience-grid');
  groups.forEach(group=>{
    const column=node('section','experience-group '+group.tone);column.append(node('h4','',group.label));
    group.tags.forEach(tag=>{
      const theme=data.themes.find(t=>t.tag===tag);
      const detail=node('details','theme-card');const header=node('summary','theme-heading');
      header.append(node('span','',theme.label),node('strong','theme-count',theme.ids.length+'건'));
      detail.append(header,node('p','theme-description',theme.description));
      const evidence=node('div','theme-evidence');
      theme.ids.forEach(id=>{const source=data.sources.find(s=>s.id===id);if(source)evidence.append(reviewCard(source));});
      detail.append(node('p','small','관련 후기 · 한 글당 한 번 집계'),evidence);column.append(detail);
    });
    grid.append(column);
  });
  result.append(grid);
  const comparison=node('aside','comparison-note');
  comparison.append(node('h3','','줄이 길다고 모두 불만은 아니에요'),node('p','','대기 언급은 17건, 기다림 부담은 5건, 빠른 회전·짧은 기다림은 7건이었어요. 서로 겹칠 수 있는 주제이며, 의견을 단순 찬반 점수로 합산하지 않았어요.'));
  result.append(comparison);
  result.append(node('p','limitations',data.limitation));
  const all=node('details','all-reviews');const allHeading=node('summary','','검토한 후기 30건 모두 보기');
  const list=node('div','sources');data.sources.forEach(source=>list.append(reviewCard(source)));
  all.append(allHeading,node('p','small','원문은 외부 사이트에서 열려요. 요약은 시장 관련 경험만 반영했고, 위치가 혼재한 부분과 개별 주장에는 확인 범위를 표시했어요.'),list);
  result.append(all);
}

async function analyze(scroll=true) {
  if(!destinations.length)return;
  pending?.abort(); pending=new AbortController(); const current=++requestId;
  const destinationId=$('destination').value; const isManual=$('period').value==='manual'; const [year,quarter]=isManual?[]:$('period').value.split('-').map(Number);
  const mode=document.querySelector('input[name="mode"]:checked').value;
  const chosen=destinations.find(d=>d.slug===destinationId);
  $('report-period').textContent=isManual?`${chosen.name} · 후기 30건 통합 검토 (2025~2026)`:`${chosen.name} · ${year}년 ${quarter}분기`; $('result').replaceChildren();$('error').hidden=true;setBusy(true);
  renderDestinations();
  if(scroll)$('report').scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'start'});
  try {
    if(isManual) {
      if(mode==='live')throw new Error('AI 웹 검색은 분기를 선택한 뒤 이용해주세요.');
      if(destinationId!=='sokcho') {
        renderResult({kind:'empty',title:'이 관광지의 통합 후기 검토는 준비 중이에요',summary:'현재 후기 30건 통합 검토는 속초관광수산시장에 제공됩니다.'});
        return;
      }
      const response=await fetch('/data/sokcho-review-pilot.json?v=reviews-30',{signal:pending.signal});
      if(!response.ok)throw new Error('후기 검토 자료를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
      const data=await response.json();if(current!==requestId)return;renderReviewPilot(data);return;
    }
    const response=await fetch('/api/analysis',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({destinationId,year,quarter,mode}),signal:pending.signal});
    const data=await response.json();if(current!==requestId)return;if(!response.ok)throw new Error(data.error || '자료를 불러오지 못했습니다.');renderResult(data);
  } catch(error) {if(current===requestId && error.name!=='AbortError'){$('error').textContent=error.message;$('error').hidden=false;}}
  finally {if(current===requestId)setBusy(false);}
}
function renderDestinations() {
  const cards=$('cards');cards.replaceChildren();
  destinations.filter(d=>activeRegion==='전체'||d.region===activeRegion).forEach(d=>{
    const card=node('article','card');const top=node('div','card-top');const stage=String(destinations.indexOf(d)+1).padStart(2,'0'); top.append(node('span','card-tag',d.region),node('span','card-number','STAGE '+stage)); const scene=node('div','stage-scene');scene.setAttribute('aria-hidden','true');const scenery={sokcho:'market',gyeongju:'temple',jeonju:'village',busan:'beach',jeju:'island',gangneung:'coast'};scene.classList.add(scenery[d.slug] || 'coast'); card.append(scene,top,node('h3','',d.name),node('p','',d.categories));
    const button=node('button','destination-button','스테이지 입장 →');button.type='button';button.setAttribute('aria-label',d.name+' 가격 정보 탐험하기');button.addEventListener('click',()=>{$('destination').value=d.slug;renderDestinations();analyze();});card.classList.toggle('selected',d.slug===$('destination').value);card.append(button);cards.append(card);
  });
}
async function initialize() {
  const now=new Date();const korea=new Date(now.getTime()+9*60*60*1000);const year=korea.getUTCFullYear();const quarter=Math.floor(korea.getUTCMonth()/3)+1;
  const periods=[];for(let i=0;i<8;i++){const index=year*4+quarter-1-i;periods.push([Math.floor(index/4),index%4+1]);}
  if(!periods.some(p=>p[0]===2026&&p[1]===3))periods.push([2026,3]);
  const manualOption=node('option','','후기 30건 통합 검토 (2025~2026)');manualOption.value='manual';$('period').append(manualOption);
  periods.forEach(([y,q])=>{const option=node('option','',`${y}년 ${q}분기`);option.value=`${y}-${q}`;$('period').append(option);});$('period').value='manual';
  try {
    const [d,s]=await Promise.all([fetch('/api/destinations'),fetch('/api/status')]);if(!d.ok||!s.ok)throw new Error('관광지 정보를 불러오지 못했습니다. 새로고침해주세요.');
    destinations=await d.json(); const status=await s.json();if(!destinations.length)throw new Error('등록된 관광지가 없습니다.');
    $('destination').replaceChildren();destinations.forEach(d=>{const o=node('option','',d.name);o.value=d.slug;$('destination').append(o);});$('destination').value='sokcho';
    $('live-mode').disabled=!status.liveAvailable;$('connection-note').textContent=status.liveAvailable?'속초 후기 30건 통합 검토 또는 분기별 AI 웹 검색을 선택할 수 있어요.':'속초 후기 30건을 수동으로 검토했어요. 실시간 검색 없이 요약과 출처를 살펴볼 수 있어요.';
    ['전체',...new Set(destinations.map(d=>d.region))].forEach(region=>{const b=node('button','filter',region);b.type='button';b.setAttribute('aria-pressed',String(region===activeRegion));b.addEventListener('click',()=>{activeRegion=region;[...$('filters').children].forEach(c=>c.setAttribute('aria-pressed',String(c===b)));renderDestinations();});$('filters').append(b);});
    renderDestinations();await analyze(false);
  } catch(error) {$('connection-note').textContent=error.message;$('error').textContent=error.message;$('error').hidden=false;}
}
$('destination').addEventListener('change',renderDestinations);
document.querySelectorAll('input[name="mode"]').forEach(input=>input.addEventListener('change',()=>{const live=input.value==='live'&&input.checked;$('period').querySelector('option[value="manual"]').disabled=live;if(live&&$('period').value==='manual')$('period').value='2026-3';}));
$('analysis-form').addEventListener('submit',event=>{event.preventDefault();analyze();});
initialize();
