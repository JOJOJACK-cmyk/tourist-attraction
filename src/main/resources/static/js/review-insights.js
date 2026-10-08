(() => {
  const count=items=>new Set(items.map(e=>e.source_id)).size;
  const node=(tag,cls,text)=>{const el=document.createElement(tag);el.className=cls;if(text!==undefined)el.textContent=text;return el;};
  function canonicalSource(source){
    try{const url=new URL(source.url);const host=url.hostname.replace(/^(www\.|m\.)/,'');
      if(host==='fmkorea.com'){const id=url.searchParams.get('document_srl')||url.pathname.match(/(?:^|\/)(\d+)(?:\/|$)/)?.[1];if(id)return 'fmkorea:'+id;}
      if(host.endsWith('dcinside.com')&&url.searchParams.has('id')&&url.searchParams.has('no'))return 'dcinside:'+url.searchParams.get('id')+':'+url.searchParams.get('no');
      if(host==='theqoo.net')return host+url.pathname.replace(/\/$/,'');
      if(host==='blog.naver.com'){
        const blog=url.searchParams.get('blogId'),post=url.searchParams.get('logNo');
        if(blog&&post)return 'naver:'+blog+':'+post;
        const match=url.pathname.match(/^\/([^/]+)\/(\d+)\/?$/);if(match)return 'naver:'+match[1]+':'+match[2];
      }
      for(const key of [...url.searchParams.keys()])if(/^(utm_|fbclid|gclid)/.test(key))url.searchParams.delete(key);
      url.searchParams.sort();return host+url.pathname+url.search;
    }catch(_){return 'source:'+source.id;}
  }
  function select(data,pilot){
    const ids=new Set(),seen=new Set();
    data.sources.forEach(source=>{const key=canonicalSource(source);if(!seen.has(key)){ids.add('pilot-'+source.id);seen.add(key);}});
    const selected=new Set();const reviews=pilot.reviews.filter(r=>ids.has(r.source_id)&&!selected.has(r.source_id)&&(selected.add(r.source_id),true));
    return {reviews,experiences:reviews.flatMap(r=>r.analysis.experiences.filter(e=>e.source_id===r.source_id&&!['hearsay','repost'].includes(e.experience_type)))};
  }
  function dimensions(data,pilot){
    const {experiences}=select(data,pilot);
    const cost=new Set(['value_satisfaction','spending_regret','unexpected_cost','price_information']);
    const definitions=[['food_cost','식비','식비'],['lodging_cost','숙박비','시장 주변 숙박비'],['service','서비스·응대','서비스·응대'],['crowding','혼잡·대기','혼잡·대기']];
    return definitions.map(([key,label])=>{
      if(key==='food_cost')label='식비';
      const items=experiences.filter(e=>key==='food_cost'?e.category==='food'&&cost.has(e.topic):key==='lodging_cost'?e.category==='lodging'&&cost.has(e.topic):key==='service'?e.topic==='service_quality':e.topic==='crowding').sort((a,b)=>Number(b.experience_type==='direct')-Number(a.experience_type==='direct')||(b.published_at||'').localeCompare(a.published_at||''));
      const positive=items.filter(e=>e.sentiment==='positive'),negative=items.filter(e=>e.sentiment==='negative'),neutral=items.filter(e=>['neutral','mixed'].includes(e.sentiment));
      const total=count(items),good=count(positive),bad=count(negative),other=count(neutral);
      let verdict=total?'개별 경험이 있어요':'판단할 후기가 부족해요';
      if(total>=3){if(good&&bad)verdict=key==='crowding'?'방문 상황에 따라 대기가 달라요':key==='service'?'응대 경험이 갈려요':'품목·이용 조건에 따라 체감이 갈려요';
        else if(good>=3)verdict=key==='crowding'?'여유로운 이용 언급이 있어요':key==='service'?'좋은 응대 언급이 모였어요':'가격 대비 만족 언급이 모였어요';
        else if(bad>=3)verdict=key==='crowding'?'대기 부담 언급이 모였어요':key==='service'?'응대 불편 언급이 모였어요':'가격 부담 언급이 모였어요';}
      const unique=entries=>[...new Map(entries.map(e=>[e.summary+'|'+(e.condition||''),e])).values()];
      const examples=unique([...negative.slice(0,2),...positive.slice(0,2),...neutral.slice(0,1)]).slice(0,4).map(e=>({text:e.summary,condition:data.year==null?[e.published_at?.slice(0,4)+'년 작성',e.condition].filter(Boolean).join(' · '):e.condition||null,sentiment:e.sentiment}));
      const conditions=[...new Set(items.map(e=>e.condition).filter(Boolean))].slice(0,3);
      return {key,label:key==='lodging_cost'?'시장 주변 숙박비':label,total,good,bad,other,verdict,examples,conditions,
        emptyText:key==='lodging_cost'?'이 기간의 시장 주변 숙박비 후기가 아직 충분하지 않아요.':key==='service'?'이 기간의 실제 응대 경험을 담은 후기가 아직 충분하지 않아요.':key==='food_cost'?'이 기간의 가격 대비 만족을 평가한 후기가 아직 충분하지 않아요.':'이 기간의 시장 인파·대기 후기가 아직 충분하지 않아요.'};
    });
  }
  function renderDimensions(aspects,target){
    const section=node('section','visitor-overview');section.append(node('h3','subheading','비용과 방문 분위기'));
    const grid=node('div','visitor-grid');
    aspects.forEach(aspect=>{const card=node('article','visitor-card '+(aspect.total?'':'insufficient'));
      card.append(node('span','card-tag',aspect.label),node('h4','',aspect.verdict));
      if(aspect.total){const labels=aspect.key==='crowding'?['여유·빠른 이용','혼잡·대기 부담']:aspect.key==='service'?['좋은 응대','응대 불편']:['가격 대비 만족','가격 부담'];
        const stats=[];if(aspect.good)stats.push(labels[0]+' '+aspect.good+'건');if(aspect.bad)stats.push(labels[1]+' '+aspect.bad+'건');if(aspect.other)stats.push('그 외 체감 '+aspect.other+'건');
        card.append(node('p','visitor-counts',stats.join(' · ')),node('p','small','관련 후기 '+aspect.total+'건'));
        const list=node('ul','visitor-experiences');aspect.examples.forEach(example=>{const row=node('li','');row.append(node('span','',example.text));if(example.condition)row.append(node('span','visitor-condition',example.condition));list.append(row);});card.append(list);
      }else card.append(node('p','small',aspect.emptyText));
      grid.append(card);
    });section.append(grid);target.append(section);
  }
  function aggregate(data,pilot){
    if(Array.isArray(data.overviewThemes))return aggregateConfigured(data,pilot);
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
    const waiting=experiences.filter(e=>e.category==='food'&&e.topic==='crowding').sort((a,b)=>Number(b.experience_type==='direct')-Number(a.experience_type==='direct')||(b.published_at||'').localeCompare(a.published_at||''));
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
  function aggregateConfigured(data,pilot){
    const {reviews,experiences}=select(data,pilot);
    const matches=(e,rule)=>(!rule.sentiment||e.sentiment===rule.sentiment)&&(!rule.items||rule.items.includes(e.item))&&(!rule.topics||rule.topics.includes(e.topic))&&(!rule.categories||rule.categories.includes(e.category));
    const themes=sentiment=>data.overviewThemes.filter(t=>t.sentiment===sentiment).map(t=>({label:t.label,count:count(experiences.filter(e=>matches(e,t)))})).filter(t=>t.count).sort((a,b)=>b.count-a.count);
    const praise=themes('positive'),complaints=themes('negative');
    const issues=(data.overviewIssues||[]).map(t=>({title:t.title,text:t.text,count:count(experiences.filter(e=>matches(e,t)))})).filter(t=>t.count);
    const summary=[];if(praise.length)summary.push(praise.slice(0,2).map(t=>t.label).join(', ')+' 언급이 있어요.');
    if(complaints.length)summary.push(complaints.slice(0,2).map(t=>t.label).join(', ')+'도 언급됐어요.');
    return {total:reviews.length,praiseCount:count(experiences.filter(e=>e.sentiment==='positive')),complaintCount:count(experiences.filter(e=>e.sentiment==='negative')),praise,complaints,issues,summary:summary.join(' ')||'아직 종합할 만한 칭찬·불만이 충분하지 않아요.'};
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
    if(data.destinationId==='seomun')summary.append(node('p','small',data.limitation));
    target.append(summary);
    renderDimensions(dimensions(data,pilot),target);
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
  window.ReviewInsights={select,aggregate,dimensions,renderDimensions,render,canonicalSource};
})();
