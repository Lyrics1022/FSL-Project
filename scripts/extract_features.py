import os
from pathlib import Path
import numpy as np
from src.preprocessing.extractor import VideoLandmarkExtractor

def main():
    dataset_dir = Path(r"c:\Users\Rose\Desktop\FSL_Project\FSL_Dataset")
    output_dir = Path(r"c:\Users\Rose\Desktop\FSL_Project\FSL_Features")
    
    if not output_dir.exists():
        output_dir.mkdir(parents=True)

    extractor = VideoLandmarkExtractor(target_seq_len=30)
    
    folders = [f for f in dataset_dir.iterdir() if f.is_dir() and f.name.startswith("FSL_Dataset_")]
    
    print(f"Found {len(folders)} labeled folders to process...")
    
    for folder in folders:
        print(f"Processing folder: {folder.name}")
        # Create corresponding output folder
        label_name = folder.name
        out_folder = output_dir / label_name
        out_folder.mkdir(exist_ok=True)

        
        videos = list(folder.glob("*.MOV"))
        for video_path in videos:
            out_path = out_folder / (video_path.stem + ".npy")
            if out_path.exists():
                continue # Skip if already processed
            
            try:
                # Extract features: Shape (30, 159)
                features = extractor.extract_from_video_file(video_path)
                np.save(out_path, features)
            except Exception as e:
                print(f"Error processing {video_path}: {e}")
                
    extractor.close()
    print("Feature extraction complete! All features saved in FSL_Features/")

if __name__ == "__main__":
    main()
