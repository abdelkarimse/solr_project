# Solr Application

A Spring Boot application that provides file search and management with Apache Solr integration.

## Prerequisites

- Git
- Docker & Docker Compose
- Java 17+ (if running locally without Docker)
- Maven (if building locally without Docker)
- NVIDIA API Key (for AI features)

## Installation

### 1. Clone the Repository

```bash
git clone https://github.com/abdelkarimse/solr_project
cd solr_project
```

### 2. Get NVIDIA API Key

To use NVIDIA AI features, you need to obtain an API key:

1. Visit [NVIDIA Build Settings](https://build.nvidia.com/settings/api-keys)
2. Sign in with your NVIDIA account (or create one if needed)
3. Click "Create API Key"
4. Copy your API key
5. Set the environment variable:

```bash
export NVIDIA_API_KEY="your-api-key-here"
```

Or add to your `.env` file in the project root:

```env
NVIDIA_API_KEY=your-api-key-here
```

## Running the Application

### Option 1: Using Docker Compose (Recommended)

```bash
docker-compose up --build
```

This will:
- Build the Java application Docker image
- Start the Spring Boot application on port 8080
- Start Apache Solr on port 8983
- Create necessary volumes and networking

Access the application at: `http://localhost:8080`

### Option 2: Running Locally

#### Build the Application

```bash
./mvnw clean package
```

#### Start Solr (via Docker)

```bash
docker-compose up -d solr
```

#### Run the Spring Boot Application

```bash
./mvnw spring-boot:run
```

The application will be available at: `http://localhost:8080`

## Features

- File upload and management
- Full-text search with Apache Solr
- Faceted search
- Search statistics
- Responsive web interface with TailwindCSS

## Configuration

### Application Properties

Edit `src/main/resources/application.properties` for custom configuration:

```properties
# Server configuration
server.port=8080
server.servlet.context-path=/

# Solr configuration
solr.host=solr
solr.port=8983
```

### Solr Configuration

The Solr instance is configured to create a collection called "Projects_Solr" by default.

## Directory Structure

```
src/
├── main/
│   ├── java/solr/
│   │   ├── controller/     #  controllers
│   │   ├── service/        # Business logic
│   │   ├── repositories/   # Data access
│   │   ├── config/         # Spring configuration
│   │   ├── data/           # Data models
│   │   └── utils/          # Utility classes
│   └── resources/
│       ├── templates/      # Thymeleaf HTML templates
│       └── static/         # CSS, JS, static files
└── test/                   # Unit and integration tests
```

## Docker Compose Services

The `docker-compose.yml` includes:

- **app**: Spring Boot application (built from Dockerfile)
- **solr**: Apache Solr 9 instance for full-text search

## Troubleshooting

### Port Already in Use

If ports 8080 or 8983 are already in use:

```bash
# Change in docker-compose.yml
ports:
  - "8081:8080"  # Change first number to available port
  - "8984:8983"  # Change first number to available port
```

### Solr Connection Issues

Ensure Solr is running and accessible:

```bash
docker-compose logs solr
```

### Clear Volumes and Restart

To reset the application:

```bash
docker-compose down -v
docker-compose up --build
```


## Environment Variables

```env
NVIDIA_API_KEY=your-nvidia-api-key
SOLR_HOST=solr
SOLR_PORT=8983
SERVER_PORT=8080
```

