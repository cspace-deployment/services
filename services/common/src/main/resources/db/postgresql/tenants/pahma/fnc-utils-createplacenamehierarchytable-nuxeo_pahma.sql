CREATE OR REPLACE FUNCTION utils.createplacenamehierarchytable()
 RETURNS void
 LANGUAGE plpgsql
AS $function$
DECLARE
BEGIN
  IF NOT EXISTS ( SELECT relname
                  FROM pg_catalog.pg_class c
                       JOIN
                       pg_catalog.pg_namespace n
                       ON (n.oid = c.relnamespace)
               WHERE c.relname = 'placename_hierarchy' 
                 AND n.nspname = 'utils' )
  THEN
    CREATE TABLE utils.placename_hierarchy (
       placecsid  text,
       placename  text,
       parentcsid text,
       nextcsid   text,
       place_hierarchy text,
       csid_hierarchy  text );

    CREATE INDEX uph_pcsid_ndx on utils.placename_hierarchy ( placecsid );
    CREATE INDEX uph_pname_ndx on utils.placename_hierarchy ( placename );
  END IF;
END;
$function$
