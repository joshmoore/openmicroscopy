-- Copyright (C) 2014 Glencoe Software, Inc. All rights reserved.
-- Use is subject to license terms supplied in LICENSE.txt
--
-- This program is free software; you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation; either version 2 of the License, or
-- (at your option) any later version.
--
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.
--
-- You should have received a copy of the GNU General Public License along
-- with this program; if not, write to the Free Software Foundation, Inc.,
-- 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
--

---
--- OMERO5 development release upgrade from OMERO5.1DEV__16 to OMERO5.1DEV__17.
---

BEGIN;

CREATE OR REPLACE FUNCTION omero_assert_db_version(version varchar, patch int) RETURNS void AS '
DECLARE
    rec RECORD;
BEGIN

    SELECT INTO rec *
           FROM dbpatch
          WHERE id = ( SELECT id FROM dbpatch ORDER BY id DESC LIMIT 1 )
            AND currentversion = version
            AND currentpatch = patch;

    IF NOT FOUND THEN
        RAISE EXCEPTION ''ASSERTION ERROR: Wrong database version'';
    END IF;

END;' LANGUAGE plpgsql;

SELECT omero_assert_db_version('OMERO5.1DEV', 16);
DROP FUNCTION omero_assert_db_version(varchar, int);


INSERT INTO dbpatch (currentVersion, currentPatch,   previousVersion,     previousPatch)
             VALUES ('OMERO5.1DEV',  17,                'OMERO5.1DEV',    16);

--
-- Actual upgrade
--

ALTER TABLE node
    ALTER COLUMN conn TYPE text;

create index _fs_deletelog_event on _fs_deletelog(event_id);
create index _fs_deletelog_file on _fs_deletelog(file_id);
create index _fs_deletelog_owner on _fs_deletelog(owner_id);
create index _fs_deletelog_group on _fs_deletelog(group_id);
create index _fs_deletelog_path on _fs_deletelog(path);
create index _fs_deletelog_name on _fs_deletelog(name);
create index _fs_deletelog_repo on _fs_deletelog(repo);

-- Add constraints to keep value and unit in sync

ALTER TABLE DetectorSettings ADD CONSTRAINT ReadOutRate_nulls check ((ReadOutRate is null and ReadOutRateunit is null) or (ReadOutRate is not null and ReadOutRateunit is not null));
ALTER TABLE Laser ADD CONSTRAINT RepetitionRate_nulls check ((RepetitionRate is null and RepetitionRateunit is null) or (RepetitionRate is not null and RepetitionRateunit is not null));
ALTER TABLE Plane ADD CONSTRAINT PositionX_nulls check ((PositionX is null and PositionXunit is null) or (PositionX is not null and PositionXunit is not null));
ALTER TABLE Plane ADD CONSTRAINT PositionZ_nulls check ((PositionZ is null and PositionZunit is null) or (PositionZ is not null and PositionZunit is not null));
ALTER TABLE Plane ADD CONSTRAINT PositionY_nulls check ((PositionY is null and PositionYunit is null) or (PositionY is not null and PositionYunit is not null));
ALTER TABLE Shape ADD CONSTRAINT StrokeWidth_nulls check ((StrokeWidth is null and StrokeWidthunit is null) or (StrokeWidth is not null and StrokeWidthunit is not null));
ALTER TABLE Shape ADD CONSTRAINT FontSize_nulls check ((FontSize is null and FontSizeunit is null) or (FontSize is not null and FontSizeunit is not null));
ALTER TABLE LightSourceSettings ADD CONSTRAINT Wavelength_nulls check ((Wavelength is null and Wavelengthunit is null) or (Wavelength is not null and Wavelengthunit is not null));
ALTER TABLE Plate ADD CONSTRAINT WellOriginX_nulls check ((WellOriginX is null and WellOriginXunit is null) or (WellOriginX is not null and WellOriginXunit is not null));
ALTER TABLE Plate ADD CONSTRAINT WellOriginY_nulls check ((WellOriginY is null and WellOriginYunit is null) or (WellOriginY is not null and WellOriginYunit is not null));
ALTER TABLE Objective ADD CONSTRAINT WorkingDistance_nulls check ((WorkingDistance is null and WorkingDistanceunit is null) or (WorkingDistance is not null and WorkingDistanceunit is not null));
ALTER TABLE Pixels ADD CONSTRAINT PhysicalSizeX_nulls check ((PhysicalSizeX is null and PhysicalSizeXunit is null) or (PhysicalSizeX is not null and PhysicalSizeXunit is not null));
ALTER TABLE Pixels ADD CONSTRAINT PhysicalSizeZ_nulls check ((PhysicalSizeZ is null and PhysicalSizeZunit is null) or (PhysicalSizeZ is not null and PhysicalSizeZunit is not null));
ALTER TABLE Pixels ADD CONSTRAINT PhysicalSizeY_nulls check ((PhysicalSizeY is null and PhysicalSizeYunit is null) or (PhysicalSizeY is not null and PhysicalSizeYunit is not null));
ALTER TABLE StageLabel ADD CONSTRAINT Z_nulls check ((Z is null and Zunit is null) or (Z is not null and Zunit is not null));
ALTER TABLE StageLabel ADD CONSTRAINT Y_nulls check ((Y is null and Yunit is null) or (Y is not null and Yunit is not null));
ALTER TABLE StageLabel ADD CONSTRAINT X_nulls check ((X is null and Xunit is null) or (X is not null and Xunit is not null));
ALTER TABLE WellSample ADD CONSTRAINT PositionX_nulls check ((PositionX is null and PositionXunit is null) or (PositionX is not null and PositionXunit is not null));
ALTER TABLE WellSample ADD CONSTRAINT PositionY_nulls check ((PositionY is null and PositionYunit is null) or (PositionY is not null and PositionYunit is not null));
ALTER TABLE Channel ADD CONSTRAINT EmissionWavelength_nulls check ((EmissionWavelength is null and EmissionWavelengthunit is null) or (EmissionWavelength is not null and EmissionWavelengthunit is not null));
ALTER TABLE Channel ADD CONSTRAINT PinholeSize_nulls check ((PinholeSize is null and PinholeSizeunit is null) or (PinholeSize is not null and PinholeSizeunit is not null));
ALTER TABLE Channel ADD CONSTRAINT ExcitationWavelength_nulls check ((ExcitationWavelength is null and ExcitationWavelengthunit is null) or (ExcitationWavelength is not null and ExcitationWavelengthunit is not null));
ALTER TABLE TransmittanceRange ADD CONSTRAINT CutOutTolerance_nulls check ((CutOutTolerance is null and CutOutToleranceunit is null) or (CutOutTolerance is not null and CutOutToleranceunit is not null));
ALTER TABLE TransmittanceRange ADD CONSTRAINT CutInTolerance_nulls check ((CutInTolerance is null and CutInToleranceunit is null) or (CutInTolerance is not null and CutInToleranceunit is not null));
ALTER TABLE TransmittanceRange ADD CONSTRAINT CutOut_nulls check ((CutOut is null and CutOutunit is null) or (CutOut is not null and CutOutunit is not null));
ALTER TABLE TransmittanceRange ADD CONSTRAINT CutIn_nulls check ((CutIn is null and CutInunit is null) or (CutIn is not null and CutInunit is not null));
ALTER TABLE Laser ADD CONSTRAINT Wavelength_nulls check ((Wavelength is null and Wavelengthunit is null) or (Wavelength is not null and Wavelengthunit is not null));
ALTER TABLE LightSource ADD CONSTRAINT Power_nulls check ((Power is null and Powerunit is null) or (Power is not null and Powerunit is not null));
ALTER TABLE ImagingEnvironment ADD CONSTRAINT AirPressure_nulls check ((AirPressure is null and AirPressureunit is null) or (AirPressure is not null and AirPressureunit is not null));
ALTER TABLE ImagingEnvironment ADD CONSTRAINT Temperature_nulls check ((Temperature is null and Temperatureunit is null) or (Temperature is not null and Temperatureunit is not null));
ALTER TABLE Plane ADD CONSTRAINT DeltaT_nulls check ((DeltaT is null and DeltaTunit is null) or (DeltaT is not null and DeltaTunit is not null));
ALTER TABLE Plane ADD CONSTRAINT ExposureTime_nulls check ((ExposureTime is null and ExposureTimeunit is null) or (ExposureTime is not null and ExposureTimeunit is not null));
ALTER TABLE Pixels ADD CONSTRAINT TimeIncrement_nulls check ((TimeIncrement is null and TimeIncrementunit is null) or (TimeIncrement is not null and TimeIncrementunit is not null));

--
-- FINISHED
--

UPDATE dbpatch SET message = 'Database updated.', finished = clock_timestamp()
    WHERE currentVersion  = 'OMERO5.1DEV' AND
          currentPatch    = 17            AND
          previousVersion = 'OMERO5.1DEV' AND
          previousPatch   = 16;

SELECT CHR(10)||CHR(10)||CHR(10)||'YOU HAVE SUCCESSFULLY UPGRADED YOUR DATABASE TO VERSION OMERO5.1DEV__17'||CHR(10)||CHR(10)||CHR(10) AS Status;

COMMIT;
