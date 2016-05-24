set schema wf00001;

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
order by isl.value, i.id |less
  