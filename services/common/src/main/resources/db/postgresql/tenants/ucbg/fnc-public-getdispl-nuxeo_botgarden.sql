CREATE OR REPLACE FUNCTION public.getdispl(text)
  RETURNS text
  LANGUAGE sql
  IMMUTABLE STRICT
AS $$
  SELECT regexp_replace($1, '^.*\)''(.+)''$', '\1')
$$
