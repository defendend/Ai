-- Add general text response settings to chats table
ALTER TABLE chats
ADD COLUMN IF NOT EXISTS response_style VARCHAR(20) DEFAULT 'professional',
ADD COLUMN IF NOT EXISTS response_length VARCHAR(20) DEFAULT 'standard',
ADD COLUMN IF NOT EXISTS language VARCHAR(20) DEFAULT 'auto',
ADD COLUMN IF NOT EXISTS include_examples BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS content_format VARCHAR(20) DEFAULT 'paragraphs';

-- Update existing rows to have default values
UPDATE chats
SET response_style = 'professional',
    response_length = 'standard',
    language = 'auto',
    include_examples = FALSE,
    content_format = 'paragraphs'
WHERE response_style IS NULL
   OR response_length IS NULL
   OR language IS NULL
   OR include_examples IS NULL
   OR content_format IS NULL;
