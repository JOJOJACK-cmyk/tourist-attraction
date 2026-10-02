(() => {
  const count=items=>new Set(items.map(e=>e.source_id)).size;
  const node=(tag,cls,text)=>{const el=document.createElement(tag);el.className=cls;if(text!==undefined)el.textContent=text;return el;};
  function select(data,pilot){
    const ids=new Set(data.sources.map(s=>'pilot-'+s.id));
    const reviews=pilot.reviews.filter(r=>ids.has(r.source_id));
    return {reviews,experiences:reviews.flatMap(r=>r.analysis.experiences)};
  }
  function aggregate(data,pilot){
    const {reviews,experiences}=select(data,pilot);
    const positive=experiences.filter(e=>e.sentiment==='positive');
    const negative=experiences.filter(e=>e.sentiment==='negative');
    const themes=(items,definitions)=>definitions.map(([label,predicate])=>({label,count:count(items.filter(predicate))})).filter(t=>t.count).sort((a,b)=>b.count-a.count);
    const praise=themes(positive,[
      ['술빵 양·가격 만족',e=>e.item==='술빵'&&e.topic==='value_satisfaction'],
      ['누룽지 오징어순대 식감',e=>e.item==='누룽지 오징어순대'&&['experience_condition','value_satisfaction'].includes(e.topic)],
      ['포장 먹거리 만족',e=>e.category==='food'&&/포장/.test(e.condition||'')],
      ['빠른 구매·회전',e=>e.category==='food'&&e.topic==='crowding'],
      ['회 신선도·식감',e=>['회','회·초밥'].includes(e.item)&&e.topic==='experience_condition'],
      ['젓갈 구매·서비스 만족',e=>e.category==='shopping'],
      ['평일 주차 여유',e=>e.category==='parking'&&e.topic==='crowding'],
      ['따뜻한 술빵 맛·질감',e=>e.item==='술빵'&&e.topic==='experience_condition'&&/갓|직후|따뜻/.test(e.condition||'')]
    ]);
    const complaints=themes(negative,[
      ['긴 대기 부담',e=>e.category==='food'&&e.topic==='crowding'],
      ['식은 술빵 맛·질감',e=>e.item==='술빵'&&e.topic==='experience_condition'],
      ['일부 간식 가격 부담',e=>e.category==='food'&&e.topic==='spending_regret'],
      ['오징어순대 조리 상태 지적',e=>e.item==='일반 오징어순대'&&e.topic==='experience_condition']
    ]);
    const issues=[];
    const waiting=experiences.filter(e=>e.category==='food'&&e.topic==='crowding');
    if(waiting.length){
      const bad=count(waiting.filter(e=>e.sentiment==='negative')),good=count(waiting.filter(e=>e.sentiment==='positive'));
      const parts=[];if(bad)parts.push('대기 부담 '+bad+'건');if(good)parts.push('빠른 구매·회전 '+good+'건');
      issues.push({title:'인기 먹거리 대기',text:parts.length?parts.join(' · ')+'. 방문 상황에 따라 반응이 달랐어요.':'대기 언급이 있지만 칭찬·불만 판단은 아직 어려워요.',count:count(waiting)});
    }
    const bread=experiences.filter(e=>e.item==='술빵'&&e.topic==='experience_condition');
    const coldBad=count(bread.filter(e=>e.sentiment==='negative'));
    const coldGood=count(bread.filter(e=>e.sentiment==='positive'&&/식은|차가|재가열/.test(e.condition||'')));
    if(coldBad||coldGood){
      const parts=[];if(coldBad)parts.push('식은 뒤 아쉬움 '+coldBad+'건');if(coldGood)parts.push('식거나 재가열해도 만족 '+coldGood+'건');
      issues.push({title:coldBad&&coldGood?'식은 술빵에 대한 호불호':'술빵을 먹는 시점',text:parts.join(' · ')+'.',count:count(bread.filter(e=>e.sentiment==='negative'||(e.sentiment==='positive'&&/식은|차가|재가열/.test(e.condition||''))))});
    }
    const price=negative.filter(e=>e.topic==='spending_regret');
    if(price.length)issues.push({title:'일부 간식 가격 부담',text:[...new Set(price.map(e=>e.item))].join('·')+'에서 가격 부담이 언급됐어요.',count:count(price)});
    const parking=experiences.filter(e=>e.category==='parking'&&e.topic==='price_information');
    if(parking.length)issues.push({title:'가게별 주차권 제공 차이',text:'구매한 가게에 따라 주차권 제공이 달랐다는 개별 언급이 있어요.',count:count(parking)});
    const summary=[];
    if(praise.length)summary.push(praise.slice(0,2).map(t=>t.label).join(', ')+'에 칭찬이 모였어요.');
    if(complaints.length)summary.push(complaints.slice(0,2).map(t=>t.label).join(', ')+'이 주요 불만이에요.');
    return {total:reviews.length,praiseCount:count(positive),complaintCount:count(negative),praise,complaints,issues,summary:summary.join(' ')||'아직 종합할 만한 칭찬·불만이 충분하지 않아요.'};
  }
  function render(data,pilot,target){
    const overview=aggregate(data,pilot);
    const summary=node('article','summary-panel');
    summary.append(node('span','confidence','후기 종합'),node('h3','',data.title),node('p','summary-text',overview.summary));
    const stats=node('div','review-stats');
    [[overview.total,'전체 후기','total'],[overview.praiseCount,'칭찬 언급','positive'],[overview.complaintCount,'불만 언급','negative']].forEach(([value,label,tone])=>{
      const card=node('div','review-stat '+tone);card.append(node('strong','',value+'건'),node('span','',label));stats.append(card);
    });
    summary.append(stats,node('p','report-meta','후기 작성일 '+data.dateFrom+' ~ '+data.dateTo),node('p','small','한 후기에 칭찬과 불만이 함께 포함될 수 있어요.'));
    target.append(summary);
    const columns=node('div','overview-columns');
    [[overview.praise,'어떤 칭찬이 많았나','positive'],[overview.complaints,'어떤 불만이 있었나','negative']].forEach(([themes,title,tone])=>{
      const column=node('section','overview-keywords '+tone);column.append(node('h3','',title));
      if(!themes.length)column.append(node('p','small','이 기간에는 뚜렷한 '+(tone==='positive'?'칭찬':'불만')+' 언급이 없어요.'));
      const list=node('ul','overview-theme-list');themes.slice(0,6).forEach(theme=>{
        const row=node('li','overview-theme');row.append(node('span','',theme.label),node('strong','',theme.count+'건'));list.append(row);
      });column.append(list);columns.append(column);
    });target.append(columns);
    if(overview.issues.length){
      const section=node('section','overview-issues');section.append(node('h3','subheading','주요 이슈'));
      const grid=node('div','overview-issue-grid');overview.issues.forEach(issue=>{
        const card=node('article','overview-issue');card.append(node('span','card-tag',issue.count===1?'개별 언급 1건':'관련 언급 '+issue.count+'건'),node('h4','',issue.title),node('p','',issue.text));grid.append(card);
      });section.append(grid);target.append(section);
    }
  }
  window.ReviewInsights={select,aggregate,render};
})();
