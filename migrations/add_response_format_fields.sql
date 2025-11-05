-- Add response format fields to chats table
ALTER TABLE chats
ADD COLUMN IF NOT EXISTS response_format VARCHAR(10) DEFAULT 'none',
ADD COLUMN IF NOT EXISTS response_schema TEXT;

-- Update existing rows to have default value
UPDATE chats
SET response_format = 'none'
WHERE response_format IS NULL;
