-- Dumped from database version 11.12
-- Dumped by pg_dump version 14.2

--
-- Name: blobtocontent; Type: VIEW; Schema: utils; Owner: nuxeo_pahma
--

CREATE VIEW utils.blobtocontent AS
 SELECT h1.id,
    h1.name AS csid,
    bc.name,
    h2.primarytype
   FROM public.hierarchy h1,
    public.blobs_common bc,
    public.hierarchy h2
  WHERE (((h1.primarytype)::text = 'Blob'::text) AND ((h1.id)::text = (bc.id)::text) AND ((bc.repositoryid)::text = (h2.id)::text));


ALTER TABLE utils.blobtocontent OWNER TO nuxeo_pahma;
