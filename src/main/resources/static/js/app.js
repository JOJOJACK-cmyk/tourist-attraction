const $ = id => document.getElementById(id);
let destinations = [], activeRegion = '전체', requestId = 0;
let pending = null;
function node(tag,className,text) { const n=document.createElement(tag); if(className)n.className=className; if(text!==undefined)n.textContent=text; return n; }
function setBusy(busy) {
  $('report').setAttribute('aria-busy',String(busy)); $('loading').hidden=!busy; $('analyze-button').disabled=busy || !destinations.length;
  $('analyze-button').textContent=busy?'후기 불러오는 중…':'후기 살펴보기 →';
}
function renderResult(data) {
  const result=$('result');result.replaceChildren();
  $('result-kind').textContent=data.kind==='live'?'AI 검색 종합':data.kind==='empty'?'자료 없음':'종합 후기';
  const summary=node('article','summary-panel');summary.append(node('h3','',data.title),node('p','summary-text',data.summary));result.append(summary);
  if(data.kind==='empty') {
    const chosen=destinations.find(d=>d.slug===$('destination').value);
    if(chosen){const intro=node('a','primary',chosen.name+' 소개와 방문 팁 보기 →');intro.href='/destinations/'+encodeURIComponent(chosen.slug);result.append(intro);}
    if(chosen?.slug==='sokcho'){
      const button=node('button','destination-button','속초 전체 후기 보기');button.type='button';
      button.addEventListener('click',()=>{$('period').querySelector('option[value="all"]').disabled=false;$('period').value='all';document.querySelector('input[name="mode"][value="sample"]').checked=true;analyze();});result.append(button);
    }
    return;
  }
  if(data.aspects?.length){
    const section=node('section','visitor-overview');section.append(node('h3','subheading','비용과 방문 분위기'));const grid=node('div','visitor-grid');
    data.aspects.forEach(aspect=>{const card=node('article','visitor-card '+(aspect.status==='grounded'?'':'insufficient'));card.append(node('span','card-tag',aspect.label),node('p','summary-text',aspect.summary));grid.append(card);});section.append(grid);result.append(section);
  }
  if(data.sources?.length && data.kind==='live'){
    const details=node('details','visitor-sources');details.append(node('summary','','참고 링크 '+data.sources.length+'개'));const list=node('ul','');
    data.sources.forEach(source=>{try{const url=new URL(source.url);if(url.protocol!=='https:'||url.username||url.password)return;const item=node('li',''),link=node('a','','출처 '+source.id);link.href=url.href;link.target='_blank';link.rel='noopener noreferrer';item.append(link);list.append(item);}catch(_){}});details.append(list);result.append(details);
  }
  if(data.searchSuggestions){const frame=node('iframe','search-suggestions');frame.title='Google 검색 제안';frame.setAttribute('sandbox','allow-popups allow-popups-to-escape-sandbox');frame.setAttribute('referrerpolicy','no-referrer');frame.srcdoc=data.searchSuggestions;result.append(frame);}
  if(data.findings?.length){
    result.append(node('h3','subheading','주요 이슈'));const list=node('div','overview-issue-grid');
    data.findings.forEach(f=>{const item=node('article','overview-issue');item.append(node('p','',f.text));list.append(item);});result.append(list);
  }
}

function selectReviewYear(data, year) {
  const sources=year===null?data.sources:data.sources.filter(s=>s.date?.startsWith(year+'-'));
  const ids=new Set(sources.map(s=>s.id));
  const themes=data.themes.map(t=>({...t,ids:t.ids.filter(id=>ids.has(id))}));
  return {...data,sources,themes,year,
    title:year===null?'속초시장, 다녀온 사람들의 이야기':year+'년 속초시장 후기',
    summary:year===null?data.summary:'선택한 연도에 작성된 후기 '+sources.length+'건에서 반복된 만족과 불편을 살펴보세요. 아래 언급 수와 근거는 이 연도의 글만 반영해요.',
    dateFrom:sources.length?sources.map(s=>s.date).sort()[0]:'',
    dateTo:sources.length?sources.map(s=>s.date).sort().at(-1):''};
}
function renderReviewPilot(data) {
  $('result').replaceChildren();
  $('result-kind').textContent='종합 후기 '+data.sources.length+'건';
  window.ReviewInsights.render(data,data.promptPilot,$('result'));
}

async function analyze(scroll=true) {
  if(!destinations.length)return;
  pending?.abort(); pending=new AbortController(); const current=++requestId;
  const destinationId=$('destination').value; const isAll=$('period').value==='all'; const year=isAll?null:Number($('period').value);
  const mode=document.querySelector('input[name="mode"]:checked').value;
  const chosen=destinations.find(d=>d.slug===destinationId);
  $('report-period').textContent=chosen.name+' · '+(isAll?'전체 연도':year+'년'); $('result').replaceChildren();$('error').hidden=true;setBusy(true);
  renderDestinations();
  if(scroll)$('report').scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'start'});
  try {
    if(mode==='sample' && (destinationId==='sokcho'||isAll)) {
      if(destinationId!=='sokcho') {
        renderResult({kind:'empty',title:'이 관광지의 후기 검토는 준비 중이에요',summary:'현재 수동 후기 검토는 속초관광수산시장에 제공됩니다.'});
        return;
      }
      const responses=await Promise.all([fetch('/data/sokcho-review-pilot.json?v=community-2',{signal:pending.signal}),fetch('/data/sokcho-prompt-pilot.json?v=community-2',{signal:pending.signal})]);
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
    const card=node('article','card');const top=node('div','card-top');top.append(node('span','card-tag',d.region)); const scene=node('div','destination-scene');scene.setAttribute('aria-hidden','true');const scenery={sokcho:'market',gyeongju:'temple',jeonju:'village',busan:'beach',jeju:'island',gangneung:'coast',seomun:'night-market',yeosu:'harbor',damyang:'bamboo'};scene.classList.add(scenery[d.slug] || 'coast'); card.append(scene,top,node('h3','',d.name),node('p','',d.categories));
    const button=node('button','destination-button','후기 살펴보기');button.type='button';button.setAttribute('aria-label',d.name+' 후기 살펴보기');button.addEventListener('click',()=>{$('destination').value=d.slug;renderDestinations();analyze();});card.classList.toggle('selected',d.slug===$('destination').value);const actions=node('div','destination-actions');
    const intro=node('a','destination-intro-link','소개와 방문 팁 →');intro.href='/destinations/'+encodeURIComponent(d.slug);intro.setAttribute('aria-label',d.name+' 소개 보기');actions.append(intro);
    actions.append(button);card.append(actions);cards.append(card);
  });
}
async function initialize() {
  const now=new Date();const korea=new Date(now.getTime()+9*60*60*1000);const year=korea.getUTCFullYear();
  const allOption=node('option','','전체 연도');allOption.value='all';$('period').append(allOption);
  for(let y=year;y>=Math.max(2020,year-4);y--){const option=node('option','',y+'년');option.value=String(y);$('period').append(option);}
  $('period').value=String(year);
  try {
    const [d,s]=await Promise.all([fetch('/api/destinations'),fetch('/api/status')]);if(!d.ok||!s.ok)throw new Error('관광지 정보를 불러오지 못했습니다. 새로고침해주세요.');
    destinations=await d.json(); const status=await s.json();if(!destinations.length)throw new Error('등록된 관광지가 없습니다.');
    $('destination').replaceChildren();destinations.forEach(d=>{const o=node('option','',d.name);o.value=d.slug;$('destination').append(o);});const requested=new URLSearchParams(location.search).get('destination');$('destination').value=destinations.some(d=>d.slug===requested)?requested:'sokcho';
    $('live-mode').disabled=!status.liveAvailable;$('connection-note').textContent='식비·숙박비·응대·혼잡도와 방문 조건을 함께 살펴보세요.';
    ['전체',...new Set(destinations.map(d=>d.region))].forEach(region=>{const b=node('button','filter',region);b.type='button';b.setAttribute('aria-pressed',String(region===activeRegion));b.addEventListener('click',()=>{activeRegion=region;[...$('filters').children].forEach(c=>c.setAttribute('aria-pressed',String(c===b)));renderDestinations();});$('filters').append(b);});
    renderDestinations();await analyze(false);
    if(location.hash==='#report')$('report').scrollIntoView({block:'start'});
  } catch(error) {$('connection-note').textContent=error.message;$('error').textContent=error.message;$('error').hidden=false;}
}
$('destination').addEventListener('change',renderDestinations);
document.querySelectorAll('input[name="mode"]').forEach(input=>input.addEventListener('change',()=>{const live=input.value==='live'&&input.checked;$('period').querySelector('option[value="all"]').disabled=live;if(live&&$('period').value==='all')$('period').value=$('period').options[1].value;}));
$('analysis-form').addEventListener('submit',event=>{event.preventDefault();analyze();});
initialize();
