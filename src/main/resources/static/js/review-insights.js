(() => {
  const categories={food:'음식',lodging:'숙박',parking:'주차',transport:'교통',admission:'입장·체험',shopping:'쇼핑',rental:'대여',amenities:'편의시설',other:'기타'};
  const topics={value_satisfaction:'가격 대비 만족',spending_regret:'소비 후회',unexpected_cost:'추가 비용',experience_condition:'이용 조건',satisfying_alternative:'다른 선택',price_information:'요금·정산 안내',service_quality:'응대',facility_condition:'시설 상태',access_convenience:'접근·이동',crowding:'혼잡·대기'};
  const tones={positive:'만족 단서',negative:'주의 단서',mixed:'조건에 따른 차이',neutral:'참고할 점'};
  const unique=items=>[...new Set(items.map(e=>e.source_id))];
  const node=(tag,cls,text)=>{const el=document.createElement(tag);el.className=cls;if(text!==undefined)el.textContent=text;return el;};
  function select(data,pilot){
    const ids=new Set(data.sources.map(s=>'pilot-'+s.id));
    const reviews=pilot.reviews.filter(r=>ids.has(r.source_id));
    const experiences=reviews.flatMap(r=>r.analysis.experiences);
    return {reviews,experiences,coverage:Object.keys(categories).map(category=>({category,count:unique(experiences.filter(e=>e.category===category)).length}))};
  }
  function group(items){
    const groups=new Map();
    items.forEach(e=>{
      const key=JSON.stringify([e.category,e.place,e.item,e.topic,e.sentiment]);
      if(!groups.has(key))groups.set(key,{category:e.category,place:e.place,item:e.item,topic:e.topic,sentiment:e.sentiment,experiences:[]});
      groups.get(key).experiences.push(e);
    });
    return [...groups.values()].map(g=>({...g,count:unique(g.experiences).length})).sort((a,b)=>b.count-a.count||a.item.localeCompare(b.item,'ko'));
  }
  function questions(experiences){
    const cards=[];
    const bread=experiences.filter(e=>e.item==='술빵'&&e.topic==='experience_condition');
    if(bread.length){
      const negative=bread.filter(e=>e.sentiment==='negative');
      const positive=bread.filter(e=>e.sentiment==='positive'&&/식은|차가|재가열/.test(e.condition||''));
      let description=negative.length?'식은 뒤 맛·질감이 아쉬웠다는 요약 '+unique(negative).length+'건이 있어요.':'먹는 시점과 온도에 따라 만족한 경험이 나왔어요.';
      if(positive.length)description+=' 식거나 재가열한 상태에 만족한 요약도 '+unique(positive).length+'건 있어요.';
      cards.push({title:'술빵은 바로 먹을까, 포장할까?',description,check:'먹을 시점과 포장 여부를 먼저 생각해보세요.',experiences:bread});
    }
    const waiting=experiences.filter(e=>e.item==='술빵'&&e.topic==='crowding');
    if(waiting.length){
      const slow=waiting.filter(e=>e.sentiment==='negative'),fast=waiting.filter(e=>e.sentiment==='positive');
      const parts=[];if(slow.length)parts.push('긴 대기 부담 '+unique(slow).length+'건');if(fast.length)parts.push('빠른 구매·회전 '+unique(fast).length+'건');
      cards.push({title:'술빵 줄, 얼마나 기다릴까?',description:parts.length?parts.join(' · ')+'. 줄이 길다는 관찰과 실제 기다린 경험은 달라요.':'대기를 언급한 요약이 있지만 만족·불만까지는 확인되지 않았어요.',check:'줄 서기 전에 수령 예상 시간과 남은 일정을 확인해보세요.',experiences:waiting});
    }
    const cost=experiences.filter(e=>['value_satisfaction','spending_regret','unexpected_cost'].includes(e.topic));
    if(cost.length){
      const items=[...new Set(cost.map(e=>e.item))];
      cards.push({title:'가격은 어떤 품목부터 확인할까?',description:items.join(' · ')+'에 대한 가격·구성 평가가 있어요. 품목마다 판단 근거를 따로 볼 수 있어요.',check:'판매 단위·양·구성·총액을 함께 확인해보세요.',experiences:cost});
    }
    const parking=experiences.filter(e=>e.category==='parking');
    if(parking.length)cards.push({title:'주차권은 구매할 가게에서 받을 수 있을까?',description:'가게마다 주차권 제공이 달랐다는 요약과 평일 주차 여유를 언급한 요약이 있어요. '+unique(parking).length+'건의 개별 자료예요.',check:'구매할 가게의 주차권 제공 여부와 적용 조건을 확인해보세요.',experiences:parking});
    return cards;
  }
  function evidence(items,data){
    const details=node('details','decision-evidence');
    details.append(node('summary','','근거 요약 '+unique(items).length+'건 보기'));
    const sourceIds=unique(items);
    sourceIds.forEach(id=>{
      const source=data.sources.find(s=>'pilot-'+s.id===id);if(!source)return;
      const card=node('article','decision-source');
      card.append(node('p','small','익명 후기 '+source.id+' · 작성일 '+source.date));
      items.filter(e=>e.source_id===id).forEach(e=>{
        const line=node('p','',e.summary);card.append(line);
        if(e.condition)card.append(node('p','small','조건 · '+e.condition));
      });
      const limitations=data.promptPilot?.reviews.find(r=>r.source_id===id)?.analysis.limitations||[];
      limitations.forEach(text=>card.append(node('p','small',text)));
      try{const url=new URL(source.url);if(url.protocol==='https:'){const link=node('a','source-link','원문 보기');link.href=url.href;link.target='_blank';link.rel='noopener noreferrer';card.append(link);}}catch{}
      details.append(card);
    });
    return details;
  }
  function render(data,pilot,target){
    const selected=select(data,pilot);
    const section=node('section','decision-section');
    section.append(node('h3','subheading','방문 전에 먼저 정할 것'));
    const cards=node('div','decision-grid');
    questions(selected.experiences).forEach(q=>{
      const card=node('article','decision-card');
      card.append(node('h4','',q.title),node('p','',q.description),node('p','decision-check',q.check),evidence(q.experiences,data));cards.append(card);
    });
    if(!cards.children.length)cards.append(node('p','local-empty','이 연도의 요약에서 방문 선택에 도움이 될 구체적인 단서를 찾지 못했어요.'));
    section.append(cards);
    const coverage=node('div','decision-coverage');
    coverage.append(node('h3','subheading','관심 있는 경험만 보기'));
    const filters=node('div','filters');filters.setAttribute('aria-label','경험 영역 선택');
    const results=node('div','decision-signals');
    const paint=category=>{
      results.replaceChildren();
      const groups=group(selected.experiences.filter(e=>category==='all'||e.category===category));
      if(!groups.length){results.append(node('p','local-empty','선택한 연도에는 '+(categories[category]||'이 영역')+' 판단에 쓸 근거가 없어요. 다른 연도나 전체 검토를 선택해보세요.'));return;}
      ['positive','negative','mixed','neutral'].forEach(tone=>{
        const subset=groups.filter(g=>g.sentiment===tone);if(!subset.length)return;
        const column=node('section','decision-tone '+tone);column.append(node('h4','',tones[tone]));
        const grid=node('div','decision-grid');
        subset.forEach(g=>{
          const card=node('article','decision-signal');
          card.append(node('span','card-tag',categories[g.category]+' · '+topics[g.topic]),node('h5','',g.item+(g.place?' · '+g.place:'')),node('p','small','관련 요약 '+g.count+'건'));
          const cases=[...new Set(g.experiences.map(e=>e.summary))];cases.slice(0,2).forEach(text=>card.append(node('p','',text)));
          const checks=[...new Set(g.experiences.map(e=>e.check_before_visit).filter(Boolean))];if(checks.length)card.append(node('p','decision-check',checks[0]));
          card.append(evidence(g.experiences,data));grid.append(card);
        });column.append(grid);results.append(column);
      });
    };
    const choices=[{category:'all',count:selected.reviews.length},...selected.coverage];
    choices.forEach(({category,count})=>{
      const button=node('button','filter',(category==='all'?'전체':categories[category])+' '+count);button.type='button';button.setAttribute('aria-pressed',String(category==='all'));
      button.addEventListener('click',()=>{[...filters.children].forEach(b=>b.setAttribute('aria-pressed',String(b===button)));paint(category);});filters.append(button);
    });
    coverage.append(filters,node('p','small','영역별 숫자는 해당 경험이 있는 요약 게시물 수예요. 한 글은 여러 영역에 포함될 수 있어요.'));
    section.append(coverage);
    const extraction=node('details','all-reviews');extraction.append(node('summary','','추출한 경험 '+selected.experiences.length+'개 살펴보기'),results);section.append(extraction);paint('all');
    // Category selection opens the selected results without replacing the useful questions above.
    [...filters.children].forEach(button=>button.addEventListener('click',()=>{extraction.open=true;}));
    const provenance=node('details','comparison-note');provenance.append(node('summary','','분석 정보'),node('p','',pilot.provenance),node('p','small','분석일 '+pilot.generatedAt+' · 선택한 입력 '+selected.reviews.length+'건'));
    pilot.limitations.forEach(text=>provenance.append(node('p','small',text)));section.append(provenance);target.append(section);
  }
  window.ReviewInsights={select,group,questions,render};
})();
