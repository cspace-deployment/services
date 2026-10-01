--
-- Name: voucherlabeltype; Type: TYPE; Schema: public; Owner: nuxeo_botgarden
--

CREATE TYPE public.voucherlabeltype AS (
	objectnumber character varying,
	determinationformatted character varying,
	family character varying,
	collectioninfo character varying,
	vouchernumber character varying,
	numbersheets integer,
	labelrequested character varying,
	gardeninfo character varying,
	vouchertype character varying,
	fieldcollectionnote character varying,
	annotation character varying,
	vouchercollectioninfo character varying
);


ALTER TYPE public.voucherlabeltype OWNER TO nuxeo_botgarden;

