import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toCollection;

class Build {
    static final File frameworkSrc = new File( "framework/src" );
    static final File appSrc = new File( "app/src" );
    static final File frameworkDist = new File( "dist/framework" );
    static final File appDist = new File( "dist/app" );

    public static void main( String[] args ) {
        timing( "Build SUCCESS", () -> {
            System.out.println( "Building..." );
            prepareCleanDir( frameworkDist, appDist );
            compile( findJavaSources( frameworkSrc ), frameworkDist );
            compile( findJavaSources( appSrc ), appDist );
        } );
    }

    private static List<File> findJavaSources( File rootDir ) {
        if ( !rootDir.isDirectory() ) {
            failBuild( "Not a directory: " + rootDir );
        }
        var files = new ArrayList<File>();
        addJavaFilesTo( files, rootDir );
        return files;
    }

    private static void addJavaFilesTo( List<File> files, File dir ) {
        Optional.ofNullable( dir.listFiles() ).ifPresent( children -> {
            for ( File child : children ) {
                if ( child.isDirectory() ) {
                    addJavaFilesTo( files, child );
                } else if ( child.getName().endsWith( ".java" ) ) {
                    files.add( child );
                }
            }
        } );
    }

    private static void compile( List<File> files, File destinationDir ) {
        var cmd = new ArrayList<String>();
        cmd.add( "javac" );
        files.stream().map( File::getPath ).collect( toCollection( () -> cmd ) );
        cmd.addAll( List.of( "-d", destinationDir.getPath() ) );
        runCommand( cmd );
    }

    private static void runCommand( List<String> cmd ) {
        timing( "Command '" + String.join( " ", cmd ) + "' executed", () -> {
            int code;
            try {
                code = new ProcessBuilder( cmd )
                        .inheritIO().start().waitFor();
            } catch ( InterruptedException | IOException e ) {
                failBuild( "Cannot run command %s: %s", cmd.get( 0 ), e );
                return;
            }
            if ( code != 0 ) {
                failBuild( "Command failed (exitCode=%d): %s",
                        code, String.join( " ", cmd ) );
            }
        } );
    }

    private static void prepareCleanDir( File... dirs ) {
        for ( File dir : dirs ) {
            if ( dir.isDirectory() ) {
                try {
                    Files.walk( dir.toPath() )
                            .sorted( Comparator.reverseOrder() )
                            .map( Path::toFile )
                            .forEach( File::delete );
                } catch ( IOException e ) {
                    failBuild( "Unable to delete dir %s due to %s", dir, e );
                }
            }
        }
    }

    private static void timing( String action, Runnable runnable ) {
        long start = System.nanoTime();
        runnable.run();
        System.out.printf( "%s in %.2f seconds%n", action,
                ( System.nanoTime() - start ) * Math.pow( 10, -9 ) );
    }

    private static void failBuild( String format, Object... args ) {
        System.err.printf( "Build FAILED: " + format, args );
        System.exit( 1 );
    }
}