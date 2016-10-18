select *
from task l,
  taskconfig lsl,
  taskdispatch li,
task i,
taskconfig isl
where l.id=lsl.id
  and li.parenttaskid=l.id
  and li.taskid=i.id
  and i.id=isl.id
  and lsl.key='slideId'
  and isl.key='slideId'; 
  and l.moduleid='SlideLoader'
  and i.moduleid='SlideImager'
  and i.status!='SUCCESS'
order by isl.value, i.id; |less

create index "slidepos_slideposlistid_idx" on slidepos (slideposlistid);
create index "slideposlist_id_idx" on slideposlist (id);
  
select spl.slideid,
  count(sp.slideposlistindex) as slideposct
from slideposlist spl,
  slidepos sp
where spl.id=sp.slideposlistid 
group by spl.slideid
order by spl.slideid; 
