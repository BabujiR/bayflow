-- Onboarding step 3: the console's Workshops page, test workshops and invite reminders.

-- Test workshops (made from the console for internal testing, without an invite) are labelled
-- so they never pass for customers.
ALTER TABLE auth_tenants ADD COLUMN is_test boolean NOT NULL DEFAULT false;

-- One reminder per invite when it is close to expiring and still unused.
ALTER TABLE auth_invitations ADD COLUMN reminded_at timestamptz;

-- The console lists workshops with a few numbers from inside each one (staff, customers,
-- locations, first admin). Those tables are per-workshop (row-level security), so this
-- owner-run function is the one reader across workshops: counts and the owner's name only.
CREATE FUNCTION auth_workshop_stats()
    RETURNS TABLE (tenant_id uuid, staff integer, customers integer, locations integer,
                   owner_username varchar, owner_email varchar)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = ${flyway:defaultSchema}
AS $$
    SELECT t.id,
           (SELECT count(DISTINCT r.user_id) FROM auth_user_roles r
             WHERE r.tenant_id = t.id AND r.role <> 'CUSTOMER')::integer,
           (SELECT count(DISTINCT r.user_id) FROM auth_user_roles r
             WHERE r.tenant_id = t.id AND r.role = 'CUSTOMER')::integer,
           (SELECT count(*) FROM auth_locations l
             WHERE l.tenant_id = t.id AND l.status = 'ACTIVE')::integer,
           o.username, o.email
    FROM auth_tenants t
    LEFT JOIN LATERAL (
        SELECT u.username, u.email
        FROM auth_users u
        JOIN auth_user_roles r ON r.user_id = u.id AND r.role = 'ADMIN'
        WHERE u.tenant_id = t.id
        ORDER BY u.created_at, u.id
        LIMIT 1
    ) o ON true
$$;

REVOKE ALL ON FUNCTION auth_workshop_stats() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_workshop_stats() TO ${app_role};
