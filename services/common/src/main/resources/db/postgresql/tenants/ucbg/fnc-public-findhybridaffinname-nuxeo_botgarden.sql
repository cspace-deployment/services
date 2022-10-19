CREATE OR REPLACE FUNCTION public.findhybridaffinname2 (tigid character varying)
  RETURNS character varying
  LANGUAGE plpgsql
  IMMUTABLE STRICT
AS $function$
  DECLARE
    tax_name VARCHAR(200);
    isa_hybrid boolean;
    aff_name VARCHAR(200);
    aff_genus VARCHAR(100);
    fhp_name VARCHAR(200);
    fhp_genus VARCHAR(100);
    mhp_name VARCHAR(200);
    mhp_genus VARCHAR(100);
    mhp_rest VARCHAR(200);
    hyb_name VARCHAR(300);

  BEGIN
    -- Get taxon name, hybrid flag, affinity taxon name, and affinity genus name.
    SELECT INTO tax_name, isa_hybrid, aff_name, aff_genus
      GETDISPL(tig.taxon),
      COALESCE(tig.hybridflag, FALSE),
      REGEXP_REPLACE(tig.affinitytaxon, '^.*\)''([^ ]+)( ?.*)''$', '\1 aff.\2'),
      REGEXP_REPLACE(tig.affinitytaxon, '^.*\)''([^ ]+)( ?.*)''$', '\1')
    FROM taxonomicidentgroup tig
    WHERE tig.id = $1;

    -- No record found. Return NULL.
    IF NOT FOUND THEN
      RETURN NULL;

    -- Is not a hybrid and no affinity taxon name. Return taxon name.
    ELSEIF isa_hybrid IS FALSE AND aff_name IS NULL THEN
      RETURN tax_name;

    -- Is not a hybrid and has affinity taxon name. Return affinity taxon name.
    ELSEIF isa_hybrid IS FALSE AND aff_name IS NOT NULL THEN
      RETURN aff_name;

    -- Is a hybrid. Return hybrid name built from 1) FHP/affinity taxon name and 2) MHP taxon name.
    ELSEIF isa_hybrid IS TRUE THEN
      -- Get female hybrid parent taxon name and genus name.
      SELECT INTO fhp_name, fhp_genus
        COALESCE(GETDISPL(fhp.taxonomicidenthybridparent), ''),
        COALESCE(REGEXP_REPLACE(fhp.taxonomicidenthybridparent, '^.*\)''([^ ]+) ?.*''$', '\1'), '')
      FROM taxonomicidentgroup tig
      JOIN hierarchy hfhp ON (hfhp.parentid = tig.id AND hfhp.name = 'taxonomicIdentHybridParentGroupList')
      JOIN taxonomicidenthybridparentgroup fhp
        ON (hfhp.id = fhp.id AND fhp.taxonomicidenthybridparentqualifier = 'female')
      WHERE tig.id = $1;

      -- Get male hybrid parent taxon, genus, and sub-generic names.
      SELECT INTO mhp_name, mhp_genus, mhp_rest
        COALESCE(GETDISPL(mhp.taxonomicidenthybridparent), ''),
        COALESCE(REGEXP_REPLACE(mhp.taxonomicidenthybridparent, '^.*\)''([^ ]+) ?.*''$', '\1'), ''),
        COALESCE(REGEXP_REPLACE(mhp.taxonomicidenthybridparent, '^.*\)''([^ ]+)( ?.*)''$', '\2'), '')
      FROM taxonomicidentgroup tig
      JOIN hierarchy hmhp ON (hmhp.parentid = tig.id AND hmhp.name = 'taxonomicIdentHybridParentGroupList')
      JOIN taxonomicidenthybridparentgroup mhp
        ON (hmhp.id = mhp.id AND mhp.taxonomicidenthybridparentqualifier = 'male')
      WHERE tig.id = $1;

      -- Set FHP taxon and genus names to use affinity taxon/genus if not null.
      fhp_name := COALESCE(aff_name, fhp_name);
      fhp_genus := COALESCE(aff_genus, fhp_genus);

      -- Check FHP and MHP genus names and build hybrid name.
      IF fhp_genus = mhp_genus THEN
         hyb_name := TRIM(fhp_name || ' × ' || SUBSTR(mhp_genus, 1, 1) || '.' || mhp_rest);
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
