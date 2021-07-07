CREATE OR REPLACE FUNCTION utils.get_updatedat_co_mh(objcsid character varying)
 RETURNS timestamp without time zone
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$

DECLARE co_mh_updatedat_return TIMESTAMP;

BEGIN

SELECT max(co_mh_updatedat) as co_mh_updatedat into co_mh_updatedat_return from
(SELECT 
   core.updatedat as co_mh_updatedat
from
   hierarchy h1
   INNER JOIN collectionobjects_common co
      ON (h1.id = co.id)
   INNER JOIN misc m
      ON (co.id = m.id AND m.lifecyclestate <> 'deleted')
   INNER JOIN collectionspace_core core on co.id=core.id
where h1.name = $1
union
select
   coremc.updatedat as co_mh_updatedat
from collectionobjects_common co
   JOIN hierarchy hrel on (co.id = hrel.id)
   JOIN relations_common rimg on (hrel.name = rimg.objectcsid and rimg.subjectdocumenttype='Media')
   JOIN hierarchy hmc on (rimg.subjectcsid = hmc.name)
   JOIN media_common mc on (mc.id=hmc.id)
   JOIN misc m on (mc.id=m.id and m.lifecyclestate<>'deleted')
   JOIN collectionspace_core coremc on mc.id=coremc.id
where hrel.name= $1 )
as derivedTable;

RETURN co_mh_updatedat_return;

END;

$function$
