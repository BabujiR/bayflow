-- Fix for V3: the trigger that copies a user's tenant onto their role rows looked up
-- auth_users without a schema. The services' connections don't have the bayflow schema on
-- their search_path (Hibernate qualifies every statement itself), so the lookup failed.
-- Pin the function's search_path to the schema Flyway manages.

CREATE OR REPLACE FUNCTION auth_user_roles_set_tenant() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = ${flyway:defaultSchema}
AS $$
BEGIN
    SELECT u.tenant_id INTO NEW.tenant_id FROM auth_users u WHERE u.id = NEW.user_id;
    RETURN NEW;
END
$$;
