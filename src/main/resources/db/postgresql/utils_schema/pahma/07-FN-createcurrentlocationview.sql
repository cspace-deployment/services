-- Dumped from database version 11.12
-- Dumped by pg_dump version 14.2

--
-- Name: createcurrentlocationview(); Type: FUNCTION; Schema: utils; Owner: nuxeo_pahma
--

CREATE FUNCTION utils.createcurrentlocationview() RETURNS void
    LANGUAGE sql
    AS $_$
    CREATE OR REPLACE VIEW utils.current_location_view AS
    SELECT
      h2.name AS collectionobjectcsid,
      REGEXP_REPLACE( m.currentlocation, '^.*\)''(.*)''$', '\1' )
        AS storagelocation,
      (CASE when ma.crate is NULL OR LENGTH(ma.crate) = 0 THEN
         NULL
      ELSE
        REGEXP_REPLACE( ma.crate, '^.*\)''(.*)''$', '\1' ) 
      END ) as crate
    FROM
      movements_common m
      JOIN movements_anthropology ma
        ON( m.id = ma.id AND m.currentlocation IS NOT NULL)
      JOIN hierarchy h1
        ON( ma.id = h1.id)
      JOIN relations_common r
        ON( h1.name = r.subjectcsid
            AND r.subjectdocumenttype = 'Movement'
            AND r.objectdocumenttype = 'CollectionObject'
            AND h1.primarytype = 'MovementTenant15' )
      JOIN hierarchy h2
        ON( r.objectcsid = h2.name )
      JOIN collectionobjects_common c
        ON( h2.id = c.id )
      JOIN misc misc
        ON( c.id = misc.id AND misc.lifecyclestate <> 'deleted' )
    ORDER BY m.locationdate DESC
$_$;


ALTER FUNCTION utils.createcurrentlocationview() OWNER TO nuxeo_pahma;

