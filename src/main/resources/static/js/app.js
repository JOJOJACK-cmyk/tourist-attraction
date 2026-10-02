const $ = id => document.getElementById(id);
let destinations = [], activeRegion = '전체', requestId = 0;
let pending = null;
function node(tag,className,text) { const n=document.createElement(tag); if(className)n.className=className; if(text!==undefined)n.textContent=text; return n; }
function sourceLink(source,label) {
  const a=node('a','source-link',label || ('익명 후기 '+source.id+' · 원문 보기'));
  try { const url=new URL(source.url); if(url.protocol!=='https:')return node('span','',a.textContent); a.href=url.href; } catch { return node('span','',a.textContent); }
  a.target='_blank'; a.rel='noopener noreferrer'; return a;
}
function setBusy(busy) {
  $('report').setAttribute('aria-busy',String(busy)); $('loading').hidden=!busy; $('analyze-button').disabled=busy || !destinations.length;
  $('analyze-button').textContent=busy?'후기 불러오는 중…':'후기 살펴보기 →';
}
function renderResult(data) {
  const result=$('result'); result.replaceChildren();
  $('result-kind').textContent=data.kind==='live'?'AI 웹 검색 결과':data.kind==='empty'?'자료 없음':'검토 자료 예시 · 신규 검색 아님';
  if(data.kind==='empty') { const box=node('div','empty'); box.append(node('h3','',data.title),node('p','',data.summary)); const b=node('button','primary','속초 전체 후기 보기'); b.type='button'; b.addEventListener('click',()=>{ $('destination').value='sokcho'; $('period').querySelector('option[value="all"]').disabled=false; $('period').value='all'; document.querySelector('input[name="mode"][value="sample"]').checked=true; analyze(); });box.append(b);result.append(box);return; }
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
    const comparisons=node('div','comparison-note');comparisons.append(node('h3','', '비교 자료는 아직 부족해요'),node('p','', '일반 동네 · 다른 관광지 · 지난해와 비교하려면 같은 품목과 규격의 자료가 더 필요합니다.'));result.append(comparisons);
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
function selectReviewYear(data, year) {
  const sources=year===null?data.sources:data.sources.filter(s=>s.date.startsWith(year+'-'));
  const ids=new Set(sources.map(s=>s.id));
  const themes=data.themes.map(t=>({...t,ids:t.ids.filter(id=>ids.has(id))}));
  return {...data,sources,themes,year,
    title:year===null?'속초시장, 다녀온 사람들의 이야기':year+'년 속초시장 후기',
    summary:year===null?data.summary:'선택한 연도에 작성된 후기 '+sources.length+'건에서 반복된 만족과 불편을 살펴보세요. 아래 언급 수와 근거는 이 연도의 글만 반영해요.',
    dateFrom:sources.length?sources.map(s=>s.date).sort()[0]:'',
    dateTo:sources.length?sources.map(s=>s.date).sort().at(-1):''};
}
function renderReviewPilot(data) {
  const result=$('result'); result.replaceChildren();
  $('result-kind').textContent='후기 '+data.sources.length+'건 · 프롬프트 적용 예시';
  const summary=node('article','summary-panel');
  summary.append(node('span','confidence','방문 전에 살펴보는 선택 단서'),node('h3','',data.title),node('p','summary-text',data.summary));
  const stats=node('div','review-stats');
  [[String(data.sources.length),'검토 게시물'],[String(data.sources.filter(s=>s.type==='개인 블로그').length),'개인 블로그'],[String(data.sources.filter(s=>s.type==='뽈레').length),'뽈레 후기']].forEach(([value,label])=>{const item=node('div','review-stat');item.append(node('strong','',value),node('span','',label));stats.append(item);});
  summary.append(stats,node('p','report-meta','작성일 '+data.dateFrom+' ~ '+data.dateTo+' · 확인일 '+data.reviewedAt));
  summary.append(node('p','period-note',data.year===null?'여러 연도를 합친 검토예요. 작성일과 실제 방문일은 다를 수 있어요.':'후기 작성 연도를 기준으로 분류했어요. 작성일과 실제 방문일은 다를 수 있어요.'));
  result.append(summary);
  if(data.promptPilot)window.ReviewInsights.render(data,data.promptPilot,result);
  const previous=node('details','all-reviews');previous.append(node('summary','','기존 주제별 검토 보기'));
  previous.append(node('h3','subheading','주제별 검토 · 펼쳐서 근거 확인'));
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
      if(!theme.ids.length)return;
      const detail=node('details','theme-card');const header=node('summary','theme-heading');
      header.append(node('span','',theme.label),node('strong','theme-count',theme.ids.length+'건'));
      detail.append(header,node('p','theme-description',theme.description));
      const evidence=node('div','theme-evidence');
      theme.ids.forEach(id=>{const source=data.sources.find(s=>s.id===id);if(source)evidence.append(reviewCard(source));});
      detail.append(node('p','small','관련 후기 · 한 글당 한 번 집계'),evidence);column.append(detail);
    });
    grid.append(column);
  });
  previous.append(grid);
  const comparison=node('aside','comparison-note');
  comparison.append(node('h3','','줄이 길다고 모두 불만은 아니에요'),node('p','',`대기 언급은 ${data.themes.find(t=>t.tag==='WS').ids.length}건, 기다림 부담은 ${data.themes.find(t=>t.tag==='WB').ids.length}건, 빠른 회전·짧은 기다림은 ${data.themes.find(t=>t.tag==='FS').ids.length}건이었어요. 서로 겹칠 수 있는 주제이며, 의견을 단순 찬반 점수로 합산하지 않았어요.`));
  previous.append(comparison);result.append(previous);
  result.append(node('p','limitations',data.limitation));
  const all=node('details','all-reviews');const allHeading=node('summary','','선택한 후기 '+data.sources.length+'건 모두 보기');
  const list=node('div','sources');data.sources.forEach(source=>list.append(reviewCard(source)));
  all.append(allHeading,node('p','small','원문은 외부 사이트에서 열려요. 요약은 시장 관련 경험만 반영했고, 위치가 혼재한 부분과 개별 주장에는 확인 범위를 표시했어요.'),list);
  result.append(all);
}

async function analyze(scroll=true) {
  if(!destinations.length)return;
  pending?.abort(); pending=new AbortController(); const current=++requestId;
  const destinationId=$('destination').value; const isAll=$('period').value==='all'; const year=isAll?null:Number($('period').value);
  const mode=document.querySelector('input[name="mode"]:checked').value;
  const chosen=destinations.find(d=>d.slug===destinationId);
  $('report-period').textContent=chosen.name+' · '+(isAll?'전체 검토 (2025~2026)':year+'년'); $('result').replaceChildren();$('error').hidden=true;setBusy(true);
  renderDestinations();
  if(scroll)$('report').scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'start'});
  try {
    if(mode==='sample' && (destinationId==='sokcho'||isAll)) {
      if(destinationId!=='sokcho') {
        renderResult({kind:'empty',title:'이 관광지의 후기 검토는 준비 중이에요',summary:'현재 수동 후기 검토는 속초관광수산시장에 제공됩니다.'});
        return;
      }
      const responses=await Promise.all([fetch('/data/sokcho-review-pilot.json?v=annual-anonymous',{signal:pending.signal}),fetch('/data/sokcho-prompt-pilot.json?v=prompt-1',{signal:pending.signal})]);
      if(responses.some(response=>!response.ok))throw new Error('후기 분석 자료를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
      const [raw,pilot]=await Promise.all(responses.map(response=>response.json()));if(current!==requestId)return;
      const data={...selectReviewYear(raw,year),promptPilot:pilot};
      if(!data.sources.length){renderResult({kind:'empty',title:'이 연도의 검토 후기가 아직 없어요',summary:year+'년에 작성된 검토 자료가 없습니다. 속초 전체 후기를 확인해 보세요.'});return;}
      renderReviewPilot(data);return;
    }
    if(isAll)throw new Error('AI 웹 검색은 연도를 선택한 뒤 이용해주세요.');
    const response=await fetch('/api/analysis',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({destinationId,year,mode}),signal:pending.signal});
    const data=await response.json();if(current!==requestId)return;if(!response.ok)throw new Error(data.error || '자료를 불러오지 못했습니다.');renderResult(data);
  } catch(error) {if(current===requestId && error.name!=='AbortError'){$('error').textContent=error.message;$('error').hidden=false;}}
  finally {if(current===requestId)setBusy(false);}
}
function renderDestinations() {
  const cards=$('cards');cards.replaceChildren();
  destinations.filter(d=>activeRegion==='전체'||d.region===activeRegion).forEach(d=>{
    const card=node('article','card');const top=node('div','card-top');top.append(node('span','card-tag',d.region)); const scene=node('div','destination-scene');scene.setAttribute('aria-hidden','true');const scenery={sokcho:'market',gyeongju:'temple',jeonju:'village',busan:'beach',jeju:'island',gangneung:'coast'};scene.classList.add(scenery[d.slug] || 'coast'); card.append(scene,top,node('h3','',d.name),node('p','',d.categories));
    const button=node('button','destination-button','관광지 선택 →');button.type='button';button.setAttribute('aria-label',d.name+' 후기 살펴보기');button.addEventListener('click',()=>{$('destination').value=d.slug;renderDestinations();analyze();});card.classList.toggle('selected',d.slug===$('destination').value);card.append(button);cards.append(card);
  });
}
async function initialize() {
  const now=new Date();const korea=new Date(now.getTime()+9*60*60*1000);const year=korea.getUTCFullYear();
  const allOption=node('option','','전체 검토 (2025~2026)');allOption.value='all';$('period').append(allOption);
  for(let y=year;y>=Math.max(2020,year-4);y--){const option=node('option','',y+'년');option.value=String(y);$('period').append(option);}
  $('period').value=String(year);
  try {
    const [d,s]=await Promise.all([fetch('/api/destinations'),fetch('/api/status')]);if(!d.ok||!s.ok)throw new Error('관광지 정보를 불러오지 못했습니다. 새로고침해주세요.');
    destinations=await d.json(); const status=await s.json();if(!destinations.length)throw new Error('등록된 관광지가 없습니다.');
    $('destination').replaceChildren();destinations.forEach(d=>{const o=node('option','',d.name);o.value=d.slug;$('destination').append(o);});$('destination').value='sokcho';
    $('live-mode').disabled=!status.liveAvailable;$('connection-note').textContent=status.liveAvailable?'속초 후기 30건 통합 검토 또는 연도별 AI 웹 검색을 선택할 수 있어요.':'속초 후기 30건을 연도별로 살펴볼 수 있어요. 실시간 검색 없이 요약과 출처를 살펴볼 수 있어요.';
    ['전체',...new Set(destinations.map(d=>d.region))].forEach(region=>{const b=node('button','filter',region);b.type='button';b.setAttribute('aria-pressed',String(region===activeRegion));b.addEventListener('click',()=>{activeRegion=region;[...$('filters').children].forEach(c=>c.setAttribute('aria-pressed',String(c===b)));renderDestinations();});$('filters').append(b);});
    renderDestinations();await analyze(false);
  } catch(error) {$('connection-note').textContent=error.message;$('error').textContent=error.message;$('error').hidden=false;}
}
$('destination').addEventListener('change',renderDestinations);
document.querySelectorAll('input[name="mode"]').forEach(input=>input.addEventListener('change',()=>{const live=input.value==='live'&&input.checked;$('period').querySelector('option[value="all"]').disabled=live;if(live&&$('period').value==='all')$('period').value=$('period').options[1].value;}));
$('analysis-form').addEventListener('submit',event=>{event.preventDefault();analyze();});
initialize();
