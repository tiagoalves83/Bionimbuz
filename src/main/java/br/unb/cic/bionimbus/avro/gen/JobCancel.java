/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package br.unb.cic.bionimbus.avro.gen;

@SuppressWarnings("all")
@org.apache.avro.specific.AvroGenerated
public class JobCancel extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {

    public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"JobCancel\",\"namespace\":\"br.unb.cic.bionimbus.avro.gen\",\"fields\":[{\"name\":\"jobID\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"files\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"avro.java.string\":\"String\"}}}]}");

    public static org.apache.avro.Schema getClassSchema() {
        return SCHEMA$;
    }
    @Deprecated
    public java.lang.String jobID;
    @Deprecated
    public java.util.List<java.lang.String> files;

    /**
     * Default constructor.
     */
    public JobCancel() {
    }

    /**
     * All-args constructor.
     */
    public JobCancel(java.lang.String jobID, java.util.List<java.lang.String> files) {
        this.jobID = jobID;
        this.files = files;
    }

    public org.apache.avro.Schema getSchema() {
        return SCHEMA$;
    }
    // Used by DatumWriter.  Applications should not call. 

    public java.lang.Object get(int field$) {
        switch (field$) {
            case 0:
                return jobID;
            case 1:
                return files;
            default:
                throw new org.apache.avro.AvroRuntimeException("Bad index");
        }
    }
    // Used by DatumReader.  Applications should not call. 

    @SuppressWarnings(value = "unchecked")
    public void put(int field$, java.lang.Object value$) {
        switch (field$) {
            case 0:
                jobID = (java.lang.String) value$;
                break;
            case 1:
                files = (java.util.List<java.lang.String>) value$;
                break;
            default:
                throw new org.apache.avro.AvroRuntimeException("Bad index");
        }
    }

    /**
     * Gets the value of the 'jobID' field.
     */
    public java.lang.String getJobID() {
        return jobID;
    }

    /**
     * Sets the value of the 'jobID' field.
     *
     * @param value the value to set.
     */
    public void setJobID(java.lang.String value) {
        this.jobID = value;
    }

    /**
     * Gets the value of the 'files' field.
     */
    public java.util.List<java.lang.String> getFiles() {
        return files;
    }

    /**
     * Sets the value of the 'files' field.
     *
     * @param value the value to set.
     */
    public void setFiles(java.util.List<java.lang.String> value) {
        this.files = value;
    }

    /**
     * Creates a new JobCancel RecordBuilder
     */
    public static br.unb.cic.bionimbus.avro.gen.JobCancel.Builder newBuilder() {
        return new br.unb.cic.bionimbus.avro.gen.JobCancel.Builder();
    }

    /**
     * Creates a new JobCancel RecordBuilder by copying an existing Builder
     */
    public static br.unb.cic.bionimbus.avro.gen.JobCancel.Builder newBuilder(br.unb.cic.bionimbus.avro.gen.JobCancel.Builder other) {
        return new br.unb.cic.bionimbus.avro.gen.JobCancel.Builder(other);
    }

    /**
     * Creates a new JobCancel RecordBuilder by copying an existing JobCancel
     * instance
     */
    public static br.unb.cic.bionimbus.avro.gen.JobCancel.Builder newBuilder(br.unb.cic.bionimbus.avro.gen.JobCancel other) {
        return new br.unb.cic.bionimbus.avro.gen.JobCancel.Builder(other);
    }

    /**
     * RecordBuilder for JobCancel instances.
     */
    public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<JobCancel>
            implements org.apache.avro.data.RecordBuilder<JobCancel> {

        private java.lang.String jobID;
        private java.util.List<java.lang.String> files;

        /**
         * Creates a new Builder
         */
        private Builder() {
            super(br.unb.cic.bionimbus.avro.gen.JobCancel.SCHEMA$);
        }

        /**
         * Creates a Builder by copying an existing Builder
         */
        private Builder(br.unb.cic.bionimbus.avro.gen.JobCancel.Builder other) {
            super(other);
        }

        /**
         * Creates a Builder by copying an existing JobCancel instance
         */
        private Builder(br.unb.cic.bionimbus.avro.gen.JobCancel other) {
            super(br.unb.cic.bionimbus.avro.gen.JobCancel.SCHEMA$);
            if (isValidValue(fields()[0], other.jobID)) {
                this.jobID = data().deepCopy(fields()[0].schema(), other.jobID);
                fieldSetFlags()[0] = true;
            }
            if (isValidValue(fields()[1], other.files)) {
                this.files = data().deepCopy(fields()[1].schema(), other.files);
                fieldSetFlags()[1] = true;
            }
        }

        /**
         * Gets the value of the 'jobID' field
         */
        public java.lang.String getJobID() {
            return jobID;
        }

        /**
         * Sets the value of the 'jobID' field
         */
        public br.unb.cic.bionimbus.avro.gen.JobCancel.Builder setJobID(java.lang.String value) {
            validate(fields()[0], value);
            this.jobID = value;
            fieldSetFlags()[0] = true;
            return this;
        }

        /**
         * Checks whether the 'jobID' field has been set
         */
        public boolean hasJobID() {
            return fieldSetFlags()[0];
        }

        /**
         * Clears the value of the 'jobID' field
         */
        public br.unb.cic.bionimbus.avro.gen.JobCancel.Builder clearJobID() {
            jobID = null;
            fieldSetFlags()[0] = false;
            return this;
        }

        /**
         * Gets the value of the 'files' field
         */
        public java.util.List<java.lang.String> getFiles() {
            return files;
        }

        /**
         * Sets the value of the 'files' field
         */
        public br.unb.cic.bionimbus.avro.gen.JobCancel.Builder setFiles(java.util.List<java.lang.String> value) {
            validate(fields()[1], value);
            this.files = value;
            fieldSetFlags()[1] = true;
            return this;
        }

        /**
         * Checks whether the 'files' field has been set
         */
        public boolean hasFiles() {
            return fieldSetFlags()[1];
        }

        /**
         * Clears the value of the 'files' field
         */
        public br.unb.cic.bionimbus.avro.gen.JobCancel.Builder clearFiles() {
            files = null;
            fieldSetFlags()[1] = false;
            return this;
        }

        @Override
        public JobCancel build() {
            try {
                JobCancel record = new JobCancel();
                record.jobID = fieldSetFlags()[0] ? this.jobID : (java.lang.String) defaultValue(fields()[0]);
                record.files = fieldSetFlags()[1] ? this.files : (java.util.List<java.lang.String>) defaultValue(fields()[1]);
                return record;
            } catch (Exception e) {
                throw new org.apache.avro.AvroRuntimeException(e);
            }
        }
    }
}
