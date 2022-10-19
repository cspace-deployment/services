CREATE OR REPLACE FUNCTION public.findhybridaffinhtml2 (tigid character varying)
  RETURNS character varying
  LANGUAGE plpgsql
  IMMUTABLE STRICT
AS $function$
  DECLARE
    taxon_name VARCHAR(200);
    taxon_name_form VARCHAR(300);
    is_hybrid BOOLEAN;
    aff_name VARCHAR(200);
    aff_name_form VARCHAR(300);
    aff_genus VARCHAR(100);
    fhp_name VARCHAR(200);
    fhp_genus VARCHAR(100);
    mhp_name VARCHAR(200);
    mhp_genus VARCHAR(100);
    mhp_rest VARCHAR(200);
    hyb_name VARCHAR(300);

  BEGIN
    -- Get taxon name, hybrid flag, affinity taxon name, and affinity genus name
    SELECT INTO taxon_name, is_hybrid, aff_name, aff_genus
      GETDISPL(tig.taxon),
      COALESCE(tig.hybridflag, FALSE),
      GETDISPL(tig.affinitytaxon),
      REGEXP_REPLACE(tig.affinitytaxon, '^.*\)''([^ ]+)( ?.*)''$', '\1')
    FROM taxonomicidentgroup tig
    WHERE tig.id = $1;

    IF NOT FOUND THEN
      RETURN NULL;

    -- Not a hybrid and no affinity taxon name.  Return formatted taxon name.
    ELSEIF is_hybrid IS FALSE AND aff_name IS NULL THEN
      SELECT INTO taxon_name_form ttg.termformatteddisplayname
      FROM taxonomicidentgroup tig
      JOIN taxon_common tc ON (tig.taxon = tc.refname)
      JOIN hierarchy h ON (tc.id = h.parentid AND h.primarytype = 'taxonTermGroup')
      JOIN taxontermgroup ttg ON (h.id = ttg.id AND taxon_name = ttg.termdisplayname)
      WHERE ttg.termformatteddisplayname IS NOT NULL
      AND tig.id = $1;

      RETURN taxon_name_form;

    -- Not a hybrid and has affinity taxon name.  Return formatted affinity taxon name.
    ELSEIF is_hybrid IS FALSE AND aff_name IS NOT NULL THEN
      SELECT INTO aff_name_form
        REGEXP_REPLACE(ttg.termformatteddisplayname, '^(<i>[^ ]+)( ?)(.*</i>.*)$', '\1</i> aff.\2<i>\3')
      FROM taxonomicidentgroup tig
      JOIN taxon_common tc ON (tig.affinitytaxon = tc.refname)
      JOIN hierarchy h ON (tc.id = h.parentid AND h.primarytype = 'taxonTermGroup')
      JOIN taxontermgroup ttg ON (h.id = ttg.id AND aff_name = ttg.termdisplayname)
      WHERE ttg.termformatteddisplayname IS NOT NULL
      AND tig.id = $1;

      RETURN aff_name_form; 

    -- Is a hybrid.  Return hybrid name built from 1) FHP/affinity taxon and 2) MHP taxon
    ELSEIF is_hybrid IS TRUE THEN
      -- Get female hybrid parent taxon and genus names.
      SELECT INTO fhp_name, fhp_genus
        COALESCE(ttg.termformatteddisplayname, ''),
        COALESCE(REGEXP_REPLACE(fhp.taxonomicidenthybridparent, '^.*\)''([^ ]+)( ?.*)''$', '\1'), '')
      FROM taxonomicidentgroup tig
      JOIN hierarchy hfhp ON (hfhp.parentid = tig.id AND hfhp.name = 'taxonomicIdentHybridParentGroupList')
      JOIN taxonomicidenthybridparentgroup fhp
        ON (hfhp.id = fhp.id AND fhp.taxonomicidenthybridparentqualifier = 'female')
      JOIN taxon_common tc ON (fhp.taxonomicidenthybridparent = tc.refname)
      JOIN hierarchy h ON (tc.id = h.parentid AND h.primarytype = 'taxonTermGroup')
      JOIN taxontermgroup ttg
        ON (h.id = ttg.id AND GETDISPL(fhp.taxonomicidenthybridparent) = ttg.termdisplayname)
      WHERE ttg.termformatteddisplayname IS NOT NULL
      AND tig.id = $1;

      -- Get male hybrid parent taxon, genus, sub-generic names.
      SELECT into mhp_name, mhp_genus, mhp_rest
        COALESCE(ttg.termformatteddisplayname, ''),
        COALESCE(REGEXP_REPLACE(mhp.taxonomicidenthybridparent, '^.*\)''([^ ]+)( .*)''$', '\1'), ''),
        COALESCE(REGEXP_REPLACE(ttg.termformatteddisplayname, '^[Xx×]? ?<i>[^ ]+( ?.*)$', '\1'), '')
      FROM taxonomicidentgroup tig
      JOIN hierarchy hmhp ON (hmhp.parentid = tig.id AND hmhp.name = 'taxonomicIdentHybridParentGroupList')
      JOIN taxonomicidenthybridparentgroup mhp
        ON (hmhp.id = mhp.id AND mhp.taxonomicidenthybridparentqualifier = 'male')
      JOIN taxon_common tc ON (mhp.taxonomicidenthybridparent = tc.refname)
      JOIN hierarchy h ON (tc.id = h.parentid AND h.primarytype = 'taxonTermGroup')
      JOIN taxontermgroup ttg
        ON (h.id = ttg.id AND GETDISPL(mhp.taxonomicidenthybridparent) = ttg.termdisplayname)
      WHERE ttg.termformatteddisplayname IS NOT NULL
      AND tig.id = $1;

      -- Set FHP taxon and genus names to use affinity taxon/genus if not null.
      fhp_genus := COALESCE(aff_genus, fhp_genus);
      fhp_name := COALESCE(aff_name_form, fhp_name);

      -- Check FHP and MHP genus names and build hybrid name
      IF fhp_genus = mhp_genus THEN
        hyb_name := TRIM(fhp_name || ' × ' || '<i>' || SUBSTR(mhp_genus, 1, 1) || '.' || mhp_rest);
      ELSE
        hyb_name := TRIM(fhp_name || ' × ' || mhp_name);
      END IF;

      -- Return hybrid name or NULL if name is empty.
      IF hyb_name = ' × ' THEN
        RETURN NULL;
      ELSE
        RETURN hyb_name;
      END IF;

    END IF;

    -- Return NULL if conditions not met.
    RETURN NULL;

  END;

$function$
