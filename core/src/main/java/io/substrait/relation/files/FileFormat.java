package io.substrait.relation.files;

import java.util.Optional;
import org.immutables.value.Value;

@Value.Enclosing
public interface FileFormat {

  @Value.Immutable
  abstract class ParquetReadOptions implements FileFormat {
    public static ImmutableFileFormat.ParquetReadOptions.Builder builder() {
      return ImmutableFileFormat.ParquetReadOptions.builder();
    }
  }

  @Value.Immutable
  abstract class ArrowReadOptions implements FileFormat {
    public static ImmutableFileFormat.ArrowReadOptions.Builder builder() {
      return ImmutableFileFormat.ArrowReadOptions.builder();
    }
  }

  @Value.Immutable
  abstract class OrcReadOptions implements FileFormat {
    public static ImmutableFileFormat.OrcReadOptions.Builder builder() {
      return ImmutableFileFormat.OrcReadOptions.builder();
    }
  }

  @Value.Immutable
  abstract class DwrfReadOptions implements FileFormat {
    public static ImmutableFileFormat.DwrfReadOptions.Builder builder() {
      return ImmutableFileFormat.DwrfReadOptions.builder();
    }
  }

  @Value.Immutable
  abstract class DelimiterSeparatedTextReadOptions implements FileFormat {
    public abstract String getFieldDelimiter();

    public abstract long getMaxLineSize();

    public abstract String getQuote();

    public abstract long getHeaderLinesToSkip();

    public abstract String getEscape();

    public abstract Optional<String> getValueTreatedAsNull();

    public static ImmutableFileFormat.DelimiterSeparatedTextReadOptions.Builder builder() {
      return ImmutableFileFormat.DelimiterSeparatedTextReadOptions.builder();
    }
  }

  @Value.Immutable
  abstract class Extension implements FileFormat {
    public abstract com.google.protobuf.Any getExtension();

    public static ImmutableFileFormat.Extension.Builder builder() {
      return ImmutableFileFormat.Extension.builder();
    }
  }
}
