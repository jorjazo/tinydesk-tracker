# Database Migrations with Liquibase

This project uses [Liquibase](https://www.liquibase.org/) for database schema management and migrations.

## Overview

Liquibase automatically manages database schema changes using versioned migration files. When the application starts, Liquibase checks which migrations have been applied and runs any new ones.

## Directory Structure

```
db/
├── changelog/
│   ├── db.changelog-master.yaml          # Master changelog file
│   └── migrations/
│       ├── 001-initial-schema.yaml       # Initial database schema
│       ├── 002-fix-history-sequence.yaml # Fix for history table sequence
│       └── [future migrations...]        # Add new migrations here
```

## Migration Files

### Current Migrations

1. **001-initial-schema.yaml** - Creates the initial database schema:
   - `videos` table - Stores video metadata and current view counts
   - `history` table - Stores historical view count snapshots
   - `metadata` table - Stores system metadata key-value pairs
   - `locks` table - Implements distributed locking for scheduled updates

2. **002-fix-history-sequence.yaml** - Fixes the history table sequence:
   - Automatically corrects sequence issues on existing databases
   - Only runs on PostgreSQL
   - Safe to run multiple times (idempotent)

## Creating New Migrations

To create a new migration:

1. Create a new YAML file in `db/changelog/migrations/` with a sequential number:
   ```
   003-your-migration-name.yaml
   ```

2. Add the migration to `db.changelog-master.yaml`:
   ```yaml
   databaseChangeLog:
     - include:
         file: db/changelog/migrations/003-your-migration-name.yaml
   ```

3. Write your changeset using Liquibase syntax:
   ```yaml
   databaseChangeLog:
     - changeSet:
         id: 003-description
         author: your-name
         changes:
           - addColumn:
               tableName: videos
               columns:
                 - column:
                     name: new_column
                     type: varchar(255)
   ```

## Liquibase Best Practices

### DO:
- ✅ Create a new migration file for each logical change
- ✅ Use descriptive IDs and comments
- ✅ Test migrations locally before deploying
- ✅ Use preconditions when appropriate
- ✅ Provide rollback statements when possible

### DON'T:
- ❌ Never modify existing migrations after they've been deployed
- ❌ Don't combine unrelated changes in one changeset
- ❌ Don't use database-specific SQL unless necessary (use Liquibase abstractions)

## Common Operations

### Add a New Column
```yaml
- changeSet:
    id: add-column-example
    author: tinydesk-tracker
    changes:
      - addColumn:
          tableName: videos
          columns:
            - column:
                name: description
                type: text
```

### Create an Index
```yaml
- changeSet:
    id: add-index-example
    author: tinydesk-tracker
    changes:
      - createIndex:
          indexName: idx_videos_published
          tableName: videos
          columns:
            - column:
                name: published_at
```

### Modify a Column
```yaml
- changeSet:
    id: modify-column-example
    author: tinydesk-tracker
    changes:
      - modifyDataType:
          tableName: videos
          columnName: title
          newDataType: varchar(2000)
```

## Testing Migrations Locally

### With Docker Compose (Recommended)
```bash
# Start fresh database
docker-compose down -v
docker-compose up -d postgres

# Build and run application
./gradlew bootJar
docker-compose up -d tinydesk-tracker

# Check logs
docker-compose logs -f tinydesk-tracker
```

### With Local PostgreSQL
```bash
# Create test database
createdb tinydesk_test

# Run with test database
export DATABASE_URL=jdbc:postgresql://localhost:5432/tinydesk_test
./gradlew bootRun
```

## Troubleshooting

### Migration Failed
If a migration fails:
1. Check the error in logs
2. Fix the migration file
3. Clear the lock (if needed):
   ```sql
   UPDATE databasechangeloglock SET locked = false;
   ```
4. Manually rollback the failed changeset:
   ```sql
   DELETE FROM databasechangelog WHERE id = 'failed-changeset-id';
   ```
5. Restart the application

### Viewing Migration History
```sql
SELECT * FROM databasechangelog ORDER BY dateexecuted DESC;
```

### Force Rerun a Migration
If you need to force a migration to run again (only in development!):
```sql
DELETE FROM databasechangelog WHERE id = 'changeset-id';
```

### Skip Liquibase on Startup
Set environment variable:
```bash
SPRING_LIQUIBASE_ENABLED=false
```

## Production Deployment

### Pre-Deployment Checklist
- [ ] Review all new migrations
- [ ] Test migrations on a database copy
- [ ] Verify rollback procedures
- [ ] Check migration performance
- [ ] Ensure backups are current

### Deployment Process
1. **Backup the database** before deployment
2. Deploy new application version
3. Liquibase runs automatically on startup
4. Monitor logs for migration success
5. Verify application functionality
6. If issues occur, rollback deployment and investigate

### Zero-Downtime Migrations
For backwards-compatible changes:
1. Deploy migration that adds new columns/tables
2. Deploy application code that uses new schema
3. Deploy cleanup migration to remove old columns/tables (after verifying)

## Additional Resources

- [Liquibase Documentation](https://docs.liquibase.com/)
- [Liquibase Best Practices](https://www.liquibase.org/get-started/best-practices)
- [Spring Boot with Liquibase](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.liquibase)

