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
  $('analyze-button').textContent=busy?'살펴보는 중…':'분위기 살펴보기';
}
function renderResult(data) {
  const result=$('result'); result.replaceChildren();
  $('result-kind').textContent=data.kind==='live'?'AI 웹 검색 결과':data.kind==='empty'?'자료 없음':'검토 자료 예시 · 신규 검색 아님';
  if(data.kind==='empty') { const box=node('div','empty'); box.append(node('h3','',data.title),node('p','',data.summary)); const b=node('button','primary','속초 2026년 3분기 예시 보기'); b.type='button'; b.addEventListener('click',()=>{ $('destination').value='sokcho'; $('period').value='2026-3'; document.querySelector('input[name="mode"][value="sample"]').checked=true; analyze(); });box.append(b);result.append(box);return; }
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
async function analyze(scroll=true) {
  if(!destinations.length)return;
  pending?.abort(); pending=new AbortController(); const current=++requestId;
  const destinationId=$('destination').value; const [year,quarter]=$('period').value.split('-').map(Number);
  const mode=document.querySelector('input[name="mode"]:checked').value;
  const chosen=destinations.find(d=>d.slug===destinationId);
  $('report-period').textContent=`${chosen.name} · ${year}년 ${quarter}분기`; $('result').replaceChildren();$('error').hidden=true;setBusy(true);
  if(scroll)$('report').scrollIntoView({behavior:'smooth',block:'start'});
  try {
    const response=await fetch('/api/analysis',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({destinationId,year,quarter,mode}),signal:pending.signal});
    const data=await response.json();if(current!==requestId)return;if(!response.ok)throw new Error(data.error || '자료를 불러오지 못했습니다.');renderResult(data);
  } catch(error) {if(current===requestId && error.name!=='AbortError'){$('error').textContent=error.message;$('error').hidden=false;}}
  finally {if(current===requestId)setBusy(false);}
}
function renderDestinations() {
  const cards=$('cards');cards.replaceChildren();
  destinations.filter(d=>activeRegion==='전체'||d.region===activeRegion).forEach(d=>{
    const card=node('article','card');const top=node('div','card-top');top.append(node('span','card-tag',d.region));card.append(top,node('h3','',d.name),node('p','',d.categories));
    const button=node('button','destination-button','분기별 분위기 보기');button.type='button';button.addEventListener('click',()=>{$('destination').value=d.slug;analyze();});card.append(button);cards.append(card);
  });
}
async function initialize() {
  const now=new Date();const korea=new Date(now.getTime()+9*60*60*1000);const year=korea.getUTCFullYear();const quarter=Math.floor(korea.getUTCMonth()/3)+1;
  const periods=[];for(let i=0;i<8;i++){const index=year*4+quarter-1-i;periods.push([Math.floor(index/4),index%4+1]);}
  if(!periods.some(p=>p[0]===2026&&p[1]===3))periods.push([2026,3]);
  periods.forEach(([y,q])=>{const option=node('option','',`${y}년 ${q}분기`);option.value=`${y}-${q}`;$('period').append(option);});$('period').value='2026-3';
  try {
    const [d,s]=await Promise.all([fetch('/api/destinations'),fetch('/api/status')]);if(!d.ok||!s.ok)throw new Error('관광지 정보를 불러오지 못했습니다. 새로고침해주세요.');
    destinations=await d.json(); const status=await s.json();if(!destinations.length)throw new Error('등록된 관광지가 없습니다.');
    $('destination').replaceChildren();destinations.forEach(d=>{const o=node('option','',d.name);o.value=d.slug;$('destination').append(o);});$('destination').value='sokcho';
    $('live-mode').disabled=!status.liveAvailable;$('connection-note').textContent=status.liveAvailable?'검토 자료 예시 또는 AI 웹 검색을 선택할 수 있어요.':'실시간 웹 검색 연결 전입니다. 검토한 속초 자료 예시를 먼저 살펴보세요.';
    ['전체',...new Set(destinations.map(d=>d.region))].forEach(region=>{const b=node('button','filter',region);b.type='button';b.setAttribute('aria-pressed',String(region===activeRegion));b.addEventListener('click',()=>{activeRegion=region;[...$('filters').children].forEach(c=>c.setAttribute('aria-pressed',String(c===b)));renderDestinations();});$('filters').append(b);});
    renderDestinations();await analyze(false);
  } catch(error) {$('connection-note').textContent=error.message;$('error').textContent=error.message;$('error').hidden=false;}
}
$('analysis-form').addEventListener('submit',event=>{event.preventDefault();analyze();});
initialize();
