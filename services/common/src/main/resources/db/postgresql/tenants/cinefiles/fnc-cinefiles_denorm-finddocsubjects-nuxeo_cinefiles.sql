CREATE OR REPLACE FUNCTION cinefiles_denorm.finddocsubjects(text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE STRICT
AS $function$
declare
   docsubjectstring text;
   r text;

begin

docsubjectstring := '';

FOR r IN
SELECT cinefiles_denorm.getdispl(ccd.item) docsubject
FROM collectionobjects_common co
LEFT OUTER JOIN collectionobjects_cinefiles_docsubjects ccd
     ON (co.id = ccd.id)
WHERE co.objectnumber = $1
ORDER BY ccd.pos

LOOP

docsubjectstring := docsubjectstring || r || '|';

END LOOP;

if docsubjectstring = '|' then docsubjectstring = null;
end if;

docsubjectstring := trim(trailing '|' from docsubjectstring);

return docsubjectstring;
end;
$function$
